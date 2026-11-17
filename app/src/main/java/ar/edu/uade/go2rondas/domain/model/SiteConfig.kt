package ar.edu.uade.go2rondas.domain.model

/**
 * Sitio de la ronda: se arma en la puesta en marcha del robot (mapa, checkpoints y misiones) y se
 * carga desde un archivo de sitio. La app lo recibe del robot. Las coordenadas están en metros, sobre
 * el plano esquemático; si hay subsuelo, se dibuja debajo de la planta baja.
 */
data class SiteConfig(
    val siteId: String,
    val name: String,
    val width: Float,
    val height: Float,
    val base: PlanPoint,
    /** Desde qué y del plano empieza el subsuelo; null si el sitio tiene una sola planta. */
    val basementStartY: Float?,
    /** Tramo de escalera entre niveles (el robot pierde precisión de ubicación ahí). */
    val stairs: PlanRect?,
    val areas: List<PlanArea>,
    val checkpoints: List<Checkpoint>,
    /** Recorrido completo desde la base; los checkpoints están sobre esta polilínea. */
    val route: List<PlanPoint>,
    val missions: List<MissionPlan>,
    val thresholds: Thresholds
) {
    fun checkpoint(id: String): Checkpoint? = checkpoints.firstOrNull { it.id == id }

    fun levelAt(point: PlanPoint): Level =
        if (basementStartY != null && point.y >= basementStartY) Level.BASEMENT else Level.GROUND_FLOOR

    fun isOnStairs(point: PlanPoint, margin: Float = 0.5f): Boolean = stairs?.let {
        point.x in it.left..it.right && point.y > it.top + margin && point.y < it.bottom - margin
    } ?: false
}

data class PlanPoint(val x: Float, val y: Float)

data class PlanRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

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

/** Umbrales del sitio. Los tiempos del enlace no vienen en el archivo: son de la app ([HmiTimings]). */
data class Thresholds(
    val batteryMinToStartPct: Int,
    val batteryWarningPct: Int,
    val batteryCriticalPct: Int,
    val staleAfterSeconds: Int = HmiTimings.STALE_AFTER_SECONDS,
    val degradedLatencyMs: Int = HmiTimings.DEGRADED_LATENCY_MS,
    val commandTimeoutSeconds: Int = HmiTimings.COMMAND_TIMEOUT_SECONDS,
    val temperature: Map<CheckpointZone, Limits>,
    val humidityLow: Limits,
    val humidityHigh: Limits,
    /** Cuánto puede subir el ruido sobre la línea base del punto (dB). */
    val noiseAboveBaseline: Limits
)

/** Límite de advertencia y límite crítico de una magnitud. */
data class Limits(val warning: Double, val critical: Double)

/** Tiempos de la interfaz: cuándo un dato se considera viejo y cuánto se espera la respuesta a una orden. */
object HmiTimings {
    const val STALE_AFTER_SECONDS = 5
    const val DEGRADED_LATENCY_MS = 1000
    const val COMMAND_TIMEOUT_SECONDS = 5
}
