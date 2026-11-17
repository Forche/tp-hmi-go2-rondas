package ar.edu.uade.go2rondas.data.site

import ar.edu.uade.go2rondas.data.remote.RobotJson
import ar.edu.uade.go2rondas.data.sim.RoutePath
import ar.edu.uade.go2rondas.domain.model.AreaKind
import ar.edu.uade.go2rondas.domain.model.Checkpoint
import ar.edu.uade.go2rondas.domain.model.CheckpointZone
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.Limits
import ar.edu.uade.go2rondas.domain.model.MissionPlan
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.PlanArea
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import ar.edu.uade.go2rondas.domain.model.PlanRect
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Thresholds
import kotlinx.serialization.SerializationException

/** El archivo no es un sitio válido. El mensaje explica qué falla, para mostrárselo al operador. */
class InvalidSiteException(message: String) : Exception(message)

/** Lo que el robot simulado necesita además del mapa: qué valores mide en cada tipo de zona. */
data class SimulationProfile(
    val humidityPct: Double,
    val typicalReadings: Map<CheckpointZone, TypicalReading>
)

data class TypicalReading(val temperatureC: Double, val noiseBaselineDba: Double)

data class SiteDefinition(val config: SiteConfig, val simulation: SimulationProfile?)

/** Lectura y validación de archivos de sitio. Aplica las mismas reglas que el servidor (site.py). */
object SiteFile {

    const val FORMAT = "go2-sitio/1"
    private const val MAX_GAP_TO_ROUTE = 0.5
    private const val DEFAULT_HUMIDITY_PCT = 45.0

    /** Misiones que puede traer el archivo; el chequeo puntual existe siempre. */
    private val FILE_MISSIONS = setOf(MissionType.FULL_ROUND, MissionType.GROUND_FLOOR_ROUND)

    fun parse(text: String, requireSimulation: Boolean = false): SiteDefinition {
        val dto = try {
            RobotJson.decodeFromString(SiteFileDto.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidSiteException("no es un archivo de sitio: ${e.message?.lineSequence()?.firstOrNull()}")
        } catch (e: IllegalArgumentException) {
            throw InvalidSiteException("no es un archivo de sitio: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        return dto.toDefinition(requireSimulation)
    }

    fun encode(dto: SiteFileDto): String = RobotJson.encodeToString(SiteFileDto.serializer(), dto)

    fun SiteFileDto.toDefinition(requireSimulation: Boolean = false): SiteDefinition {
        check(format == FORMAT) { "el formato tiene que ser \"$FORMAT\"" }
        check(route.size >= 2) { "el recorrido necesita al menos dos puntos" }
        check(checkpoints.map { it.id }.toSet().size == checkpoints.size) { "hay ids de checkpoint repetidos" }
        check(stairs == null || basementStartY != null) { "hay escalera pero no hay subsuelo" }
        if (requireSimulation) check(simulation != null) { "falta la sección \"simulation\" para el robot simulado" }

        val temperature = thresholds.temperature.entries.associate { (zone, limits) ->
            zoneOf(zone, "umbrales") to limits.toDomain()
        }
        val typical = simulation?.typicalReadings?.entries?.associate { (zone, reading) ->
            zoneOf(zone, "lecturas típicas") to TypicalReading(reading.temperatureC, reading.noiseDba)
        }
        val path = RoutePath(route.map { it.toDomain() })
        val levelAt = { y: Float -> if (basementStartY != null && y >= basementStartY) Level.BASEMENT else Level.GROUND_FLOOR }
        val domainCheckpoints = checkpoints.map { cp ->
            val zone = zoneOf(cp.zone, cp.id)
            val position = PlanPoint(cp.x, cp.y)
            check(zone in temperature) { "${cp.id}: faltan los umbrales de temperatura de ${cp.zone}" }
            check(typical == null || zone in typical) { "${cp.id}: faltan las lecturas típicas de ${cp.zone}" }
            check(path.gapTo(position) <= MAX_GAP_TO_ROUTE) { "${cp.id} no está sobre el recorrido" }
            Checkpoint(cp.id, cp.number, cp.name, levelAt(cp.y), zone, position)
        }
        val ids = domainCheckpoints.map { it.id }.toSet()
        val plans = missions.map { mission ->
            val type = MissionType.entries.firstOrNull { it.name == mission.type && it in FILE_MISSIONS }
                ?: fail("tipo de misión desconocido \"${mission.type}\"")
            check(mission.checkpoints.isNotEmpty()) { "la misión ${mission.type} no tiene checkpoints" }
            mission.checkpoints.forEach { check(it in ids) { "la misión ${mission.type} usa $it, que no existe" } }
            MissionPlan(type, mission.checkpoints)
        }
        check(plans.any { it.type == MissionType.FULL_ROUND }) { "falta la misión FULL_ROUND" }

        val config = SiteConfig(
            siteId = siteId,
            name = name,
            width = width,
            height = height,
            base = base.toDomain(),
            basementStartY = basementStartY,
            stairs = stairs?.let { PlanRect(it.left, it.top, it.right, it.bottom) },
            areas = areas.map { area ->
                val kind = AreaKind.entries.firstOrNull { it.name == area.kind }
                    ?: fail("tipo de área desconocido \"${area.kind}\"")
                PlanArea(kind, area.label, area.left, area.top, area.width, area.height)
            },
            checkpoints = domainCheckpoints,
            route = route.map { it.toDomain() },
            missions = plans + MissionPlan(MissionType.SPOT_CHECK, emptyList()),
            thresholds = Thresholds(
                batteryMinToStartPct = thresholds.battery.minToStart,
                batteryWarningPct = thresholds.battery.warning,
                batteryCriticalPct = thresholds.battery.critical,
                temperature = temperature,
                humidityLow = thresholds.humidityLow.toDomain(),
                humidityHigh = thresholds.humidityHigh.toDomain(),
                noiseAboveBaseline = thresholds.noiseAboveBaseline.toDomain()
            )
        )
        val profile = typical?.let { SimulationProfile(simulation?.humidityPct ?: DEFAULT_HUMIDITY_PCT, it) }
        return SiteDefinition(config, profile)
    }

    private fun zoneOf(name: String, where: String): CheckpointZone =
        CheckpointZone.entries.firstOrNull { it.name == name } ?: fail("$where: zona desconocida \"$name\"")

    private fun PointDto.toDomain() = PlanPoint(x, y)

    private fun LimitsDto.toDomain() = Limits(warning, critical)

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidSiteException(message())
    }

    private fun fail(message: String): Nothing = throw InvalidSiteException(message)
}
