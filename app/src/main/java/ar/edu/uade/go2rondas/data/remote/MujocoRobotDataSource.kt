package ar.edu.uade.go2rondas.data.remote

import android.util.Log
import ar.edu.uade.go2rondas.core.coroutines.ApplicationScope
import ar.edu.uade.go2rondas.core.network.MujocoServerAddress
import ar.edu.uade.go2rondas.data.mapper.toDomain
import ar.edu.uade.go2rondas.data.mapper.toDto
import ar.edu.uade.go2rondas.data.mapper.toOutcome
import ar.edu.uade.go2rondas.data.photo.PhotoStore
import ar.edu.uade.go2rondas.data.remote.dto.AlertsDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandResponseDto
import ar.edu.uade.go2rondas.data.remote.dto.EventDto
import ar.edu.uade.go2rondas.data.remote.dto.PhotoMessageDto
import ar.edu.uade.go2rondas.data.remote.dto.ServerMessage
import ar.edu.uade.go2rondas.data.remote.dto.TelemetryDto
import ar.edu.uade.go2rondas.data.remote.dto.SiteMessageDto
import ar.edu.uade.go2rondas.data.site.InvalidSiteException
import ar.edu.uade.go2rondas.data.site.SiteFile.toDefinition
import ar.edu.uade.go2rondas.data.site.SiteFileStore
import ar.edu.uade.go2rondas.data.source.RobotDataSource
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Telemetry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robot Go2-02: la simulación corre en el servidor MuJoCo y llega por WebSocket con el contrato JSON del TP.
 *
 * Igual que con el simulador local, la app no recibe un aviso de "me desconecté": si el servidor deja de
 * mandar telemetría, el repositorio lo deduce por la antigüedad del dato. Si el socket se cae, se reintenta
 * cada pocos segundos mientras el robot siga seleccionado.
 */
@Singleton
class MujocoRobotDataSource @Inject constructor(
    private val client: OkHttpClient,
    @param:MujocoServerAddress val address: String,
    private val siteStore: SiteFileStore,
    private val photoStore: PhotoStore,
    @param:ApplicationScope private val scope: CoroutineScope
) : RobotDataSource {

    // Sin replay: al volver a elegir este robot no debe aparecer la telemetría vieja como si fuera nueva.
    private val _telemetry = MutableSharedFlow<Telemetry>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 64)
    // Hasta que el robot mande su mapa: la última copia recibida o, la primera vez, el sitio de ejemplo.
    private val _site = MutableStateFlow((siteStore.loadReceivedSite(ROBOT_ID) ?: siteStore.defaultSite()).config)

    /** Órdenes enviadas que esperan su command_response, por id. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CommandOutcome>>()

    // Los callbacks de OkHttp llegan en sus propios hilos: el estado del socket se toca bajo este lock.
    private val lock = Any()
    private var running = false
    private var socket: WebSocket? = null
    private var isOpen = false
    private var incoming: Channel<String>? = null
    private var readerJob: Job? = null
    private var reconnectJob: Job? = null

    override val telemetry: Flow<Telemetry> = _telemetry.asSharedFlow()
    override val alerts: Flow<List<Alert>> = _alerts.asStateFlow()
    override val events: Flow<RobotEvent> = _events.asSharedFlow()
    override val site: StateFlow<SiteConfig> = _site.asStateFlow()

    override fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            // Las alertas de una conexión anterior ya no valen: el servidor manda la lista completa al conectar.
            _alerts.value = emptyList()
            // Los mensajes se procesan en orden en una sola corrutina (OkHttp los entrega en su hilo).
            val channel = Channel<String>(Channel.UNLIMITED)
            incoming = channel
            readerJob = scope.launch { for (text in channel) handle(text) }
            openSocket()
        }
    }

    override fun stop() {
        synchronized(lock) {
            running = false
            reconnectJob?.cancel()
            socket?.close(NORMAL_CLOSURE, null)
            socket = null
            isOpen = false
            incoming?.close()
            incoming = null
            readerJob = null
        }
    }


    override suspend fun send(command: Command): CommandOutcome {
        val ws = synchronized(lock) { socket.takeIf { isOpen } } ?: awaitCancellation()
        val response = CompletableDeferred<CommandOutcome>()
        pending[command.id] = response
        try {
            val text = RobotJson.encodeToString(CommandDto.serializer(), command.toDto(ROBOT_ID))
            // Si el socket se cerró en el medio la orden se pierde; el repositorio la marca "sin respuesta".
            if (!ws.send(text)) awaitCancellation()
            return response.await()
        } finally {
            pending.remove(command.id)
        }
    }

    private fun openSocket() {
        val request = Request.Builder().url("ws://$address/").build()
        socket = client.newWebSocket(request, Listener())
    }

    private suspend fun handle(text: String) {
        val message = try {
            RobotJson.decodeFromString(ServerMessage.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // Un mensaje que no respeta el contrato se descarta: no debe cortar la conexión.
            Log.w(TAG, "Mensaje inválido del servidor: ${text.take(200)}", e)
            return
        }
        when (message) {
            is TelemetryDto -> message.toDomain()?.let { _telemetry.emit(it) }
            is AlertsDto -> _alerts.value = message.alerts.mapNotNull { it.toDomain() }
            is EventDto -> message.toDomain()?.let { _events.emit(it) }
            is CommandResponseDto -> pending[message.commandId]?.complete(message.toOutcome())
            is SiteMessageDto -> onSiteReceived(message)
            is PhotoMessageDto -> onPhotoReceived(message)
        }
    }

    /** El robot manda su mapa al conectarse. Se guarda para seguir mostrándolo si se corta el enlace. */
    private fun onSiteReceived(message: SiteMessageDto) {
        try {
            _site.value = message.site.toDefinition().config
            siteStore.saveReceivedSite(ROBOT_ID, message.site)
        } catch (e: InvalidSiteException) {
            Log.w(TAG, "El robot mandó un mapa inválido: ${e.message}")
        }
    }

    /** Foto de la cámara del robot: se guarda para mostrarla en la alerta y en el historial. */
    private fun onPhotoReceived(message: PhotoMessageDto) {
        val jpeg = try {
            Base64.getDecoder().decode(message.jpegBase64)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Foto inválida del robot: ${message.photoId}")
            return
        }
        if (!photoStore.save(message.photoId, jpeg)) Log.w(TAG, "No se pudo guardar la foto ${message.photoId}")
    }

    private fun onSocketLost(webSocket: WebSocket) {
        synchronized(lock) {
            if (webSocket !== socket) return
            socket = null
            isOpen = false
            if (!running) return
            reconnectJob = scope.launch {
                delay(RECONNECT_DELAY_MILLIS)
                synchronized(lock) { if (running && socket == null) openSocket() }
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) { if (webSocket === socket) isOpen = true }
            Log.i(TAG, "Conectado a $address")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronized(lock) { if (webSocket === socket) incoming?.trySend(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onSocketLost(webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.d(TAG, "Sin conexión con $address: ${t.message}")
            onSocketLost(webSocket)
        }
    }

    companion object {
        const val ROBOT_ID = "go2-02"
        private const val TAG = "MujocoRobot"
        private const val NORMAL_CLOSURE = 1000
        private const val RECONNECT_DELAY_MILLIS = 3_000L
    }
}
