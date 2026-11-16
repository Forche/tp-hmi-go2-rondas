package ar.edu.uade.go2rondas.domain.model

/** Estado del enlace calculado por la app a partir de la llegada de telemetría. */
data class ConnectionStatus(
    val state: LinkState,
    val lastUpdateMillis: Long?,
    val latencyMs: Int?
) {
    companion object {
        val Disconnected = ConnectionStatus(LinkState.DISCONNECTED, null, null)
    }
}

enum class LinkState { CONNECTED, DEGRADED, DISCONNECTED }
