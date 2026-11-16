package ar.edu.uade.go2rondas.domain.model

/**
 * Configuración fija del sitio: se arma en la puesta en marcha del robot y casi no cambia.
 * Las coordenadas están en metros, sobre el plano esquemático del data center.
 */
data class SiteConfig(
    val siteId: String,
    val name: String,
    val width: Float,
    val height: Float,
    val base: PlanPoint,
    val areas: List<PlanArea>,
    val checkpoints: List<Checkpoint>,
    /** Recorrido completo desde la base; los checkpoints están sobre esta polilínea. */
    val route: List<PlanPoint>,
    val missions: List<MissionPlan>,
    val thresholds: Thresholds
) {
    fun checkpoint(id: String): Checkpoint? = checkpoints.firstOrNull { it.id == id }
}

data class PlanPoint(val x: Float, val y: Float)

data class PlanArea(
    val kind: AreaKind,
    val label: String?,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

enum class AreaKind { ROOM, RACK_ROW, COLD_AISLE, EQUIPMENT, STAIRS, LEVEL_LABEL }

enum class Level { GROUND_FLOOR, BASEMENT }

data class Checkpoint(
    val id: String,
    val number: Int,
    val name: String,
    val level: Level,
    val zone: CheckpointZone,
    val position: PlanPoint
)

/** Tipo de punto: define qué umbral de temperatura se aplica. */
enum class CheckpointZone { COLD_AISLE, HOT_AISLE, COOLING, NETWORK, ELECTRICAL }

enum class MissionType { FULL_ROUND, GROUND_FLOOR_ROUND, SPOT_CHECK }

data class MissionPlan(val type: MissionType, val checkpointIds: List<String>)

data class Thresholds(
    val batteryMinToStartPct: Int,
    val batteryWarningPct: Int,
    val batteryCriticalPct: Int,
    val staleAfterSeconds: Int,
    val degradedLatencyMs: Int,
    val commandTimeoutSeconds: Int,
    val temperature: Map<CheckpointZone, Limits>,
    val humidityLow: Limits,
    val humidityHigh: Limits,
    /** Cuánto puede subir el ruido sobre la línea base del punto (dB). */
    val noiseAboveBaseline: Limits
)

/** Límite de advertencia y límite crítico de una magnitud. */
data class Limits(val warning: Double, val critical: Double)
