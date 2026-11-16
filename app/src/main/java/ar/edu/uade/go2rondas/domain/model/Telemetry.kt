package ar.edu.uade.go2rondas.domain.model

/** Lo que el robot informa en cada ciclo de telemetría. */
data class Telemetry(
    val robotId: String,
    val sequence: Long,
    val timestampMillis: Long,
    val link: LinkQuality,
    val battery: Battery,
    val state: OperationalState,
    val mission: MissionProgress?,
    val location: RobotLocation,
    val motion: Motion,
    val activeAlerts: Int,
    /** Última lectura ambiental: se mide al llegar a cada checkpoint. */
    val environment: EnvironmentReading?
)

data class LinkQuality(val latencyMs: Int, val rssiDbm: Int)

data class Battery(val percent: Int, val charging: Boolean, val estimatedMinutes: Int)

enum class OperationalState { AT_BASE, ON_MISSION, PAUSED, AWAITING_DECISION, RETURNING, STOPPED }

data class MissionProgress(
    val missionId: String,
    val type: MissionType,
    val currentCheckpointId: String?,
    val completed: Int,
    val total: Int,
    val elapsedSeconds: Long
)

data class RobotLocation(
    val level: Level,
    /** Tramo actual: null significa la base. */
    val fromCheckpointId: String?,
    val toCheckpointId: String?,
    val position: PlanPoint,
    val headingDeg: Int,
    /** Confianza de la localización contra el mapa, de 0 a 1. */
    val confidence: Double
)

data class Motion(val speedMps: Double, val moving: Boolean)

data class EnvironmentReading(
    val checkpointId: String,
    val temperatureC: Double,
    val humidityPct: Double,
    val noiseDba: Double,
    val noiseBaselineDba: Double
)
