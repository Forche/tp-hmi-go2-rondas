package ar.edu.uade.go2rondas.data.remote.dto

import ar.edu.uade.go2rondas.data.site.SiteFileDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mensajes del contrato JSON del TP (no es el protocolo oficial de Unitree).
// El campo "type" de cada mensaje del servidor elige la clase al decodificar.

/** Lo que llega del servidor. */
@Serializable
sealed interface ServerMessage

/** El mapa que tiene cargado el robot; lo manda al conectarse la app. */
@Serializable
@SerialName("site")
data class SiteMessageDto(val site: SiteFileDto) : ServerMessage

@Serializable
@SerialName("telemetry")
data class TelemetryDto(
    @SerialName("robot_id") val robotId: String,
    val seq: Long,
    val timestamp: Long,
    val connection: LinkDto,
    val battery: BatteryDto,
    val state: String,
    val mission: MissionProgressDto? = null,
    val location: LocationDto,
    val motion: MotionDto,
    @SerialName("active_alerts") val activeAlerts: Int,
    val environment: ReadingDto? = null
) : ServerMessage

@Serializable
data class LinkDto(
    @SerialName("latency_ms") val latencyMs: Int,
    @SerialName("rssi_dbm") val rssiDbm: Int
)

@Serializable
data class BatteryDto(
    val percent: Int,
    val charging: Boolean,
    @SerialName("est_minutes") val estimatedMinutes: Int
)

@Serializable
data class MissionProgressDto(
    @SerialName("mission_id") val missionId: String,
    val type: String,
    @SerialName("current_cp") val currentCheckpointId: String? = null,
    @SerialName("current_position") val currentPosition: Int,
    val completed: Int,
    val total: Int,
    @SerialName("elapsed_s") val elapsedSeconds: Long
)

@Serializable
data class LocationDto(
    val level: String,
    @SerialName("from_cp") val fromCheckpointId: String? = null,
    @SerialName("to_cp") val toCheckpointId: String? = null,
    val x: Float,
    val y: Float,
    @SerialName("heading_deg") val headingDeg: Int,
    val confidence: Double
)

@Serializable
data class MotionDto(
    @SerialName("speed_mps") val speedMps: Double,
    val moving: Boolean
)

@Serializable
data class ReadingDto(
    @SerialName("checkpoint_id") val checkpointId: String,
    @SerialName("temperature_c") val temperatureC: Double,
    @SerialName("humidity_pct") val humidityPct: Double,
    @SerialName("noise_dba") val noiseDba: Double,
    @SerialName("noise_baseline_dba") val noiseBaselineDba: Double
)

@Serializable
@SerialName("alerts")
data class AlertsDto(val alerts: List<AlertDto>) : ServerMessage

@Serializable
data class AlertDto(
    val id: String,
    val timestamp: Long,
    val severity: String,
    val code: String,
    @SerialName("checkpoint_id") val checkpointId: String? = null,
    val measured: Double? = null,
    val threshold: Double? = null,
    @SerialName("requires_decision") val requiresDecision: Boolean,
    val options: List<String> = emptyList(),
    val acknowledged: Boolean,
    @SerialName("photo_id") val photoId: String? = null
)

/** Un solo tipo para los eventos: cada `kind` usa los campos que le corresponden. */
@Serializable
@SerialName("event")
data class EventDto(
    val kind: String,
    val timestamp: Long,
    @SerialName("mission_id") val missionId: String? = null,
    @SerialName("mission_type") val missionType: String? = null,
    val total: Int? = null,
    @SerialName("checkpoint_id") val checkpointId: String? = null,
    val status: String? = null,
    val reading: ReadingDto? = null,
    val alert: AlertDto? = null,
    val result: String? = null,
    val completed: Int? = null
) : ServerMessage

/** Foto JPEG de la cámara frontal: la de un checkpoint inspeccionado o la de un obstáculo. */
@Serializable
@SerialName("photo")
data class PhotoMessageDto(
    @SerialName("photo_id") val photoId: String,
    val timestamp: Long,
    @SerialName("jpeg_base64") val jpegBase64: String
) : ServerMessage

/** Cámara en vivo: una imagen JPEG de la cámara frontal, mientras la app la pida. No se guarda. */
@Serializable
@SerialName("camera_frame")
data class CameraFrameDto(
    val timestamp: Long,
    @SerialName("jpeg_base64") val jpegBase64: String
) : ServerMessage

@Serializable
@SerialName("command_response")
data class CommandResponseDto(
    @SerialName("command_id") val commandId: String,
    val status: String,
    val reason: String? = null
) : ServerMessage

/** Lo que envía la app. */

/** Pide (o corta) la cámara en vivo. */
@Serializable
data class CameraRequestDto(val type: String = "camera", val enabled: Boolean)

@Serializable
data class CommandDto(
    val type: String = "command",
    @SerialName("command_id") val commandId: String,
    @SerialName("robot_id") val robotId: String,
    val name: String,
    val params: CommandParamsDto = CommandParamsDto()
)

@Serializable
data class CommandParamsDto(
    val mission: String? = null,
    @SerialName("checkpoint_id") val checkpointId: String? = null,
    @SerialName("alert_id") val alertId: String? = null,
    val decision: String? = null
)
