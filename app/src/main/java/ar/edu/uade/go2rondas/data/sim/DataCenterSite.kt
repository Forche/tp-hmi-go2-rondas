package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.domain.model.AreaKind
import ar.edu.uade.go2rondas.domain.model.Checkpoint
import ar.edu.uade.go2rondas.domain.model.CheckpointZone
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.Limits
import ar.edu.uade.go2rondas.domain.model.MissionPlan
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.PlanArea
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Thresholds

/**
 * Sitio del caso de uso: data center genérico con planta baja y subsuelo (sección 2 del documento).
 * En un robot real esto sale de la puesta en marcha (mapa del LiDAR + checkpoints marcados);
 * acá es fijo. Coordenadas en metros; el subsuelo se dibuja debajo de la planta baja.
 */
object DataCenterSite {

    private const val BASEMENT_START_Y = 14f

    fun levelAt(point: PlanPoint): Level = if (point.y >= BASEMENT_START_Y) Level.BASEMENT else Level.GROUND_FLOOR

    val config = SiteConfig(
        siteId = "dc-v1",
        name = "Data center – Edificio A",
        width = 32.8f,
        height = 22f,
        base = PlanPoint(3.3f, 12.5f),
        areas = listOf(
            PlanArea(AreaKind.LEVEL_LABEL, "Planta baja", 1f, 0.4f, 0f, 0f),
            PlanArea(AreaKind.ROOM, "NOC", 0.8f, 2.2f, 5.2f, 12f),
            PlanArea(AreaKind.ROOM, null, 6.8f, 2.2f, 19.6f, 12f),
            PlanArea(AreaKind.COLD_AISLE, null, 9.2f, 6.2f, 12f, 4f),
            PlanArea(AreaKind.RACK_ROW, "Fila A", 9.2f, 4.8f, 12f, 1.4f),
            PlanArea(AreaKind.RACK_ROW, "Fila B", 9.2f, 10.2f, 12f, 1.4f),
            PlanArea(AreaKind.EQUIPMENT, "Clima", 22.6f, 4.4f, 3f, 4f),
            PlanArea(AreaKind.EQUIPMENT, "Red", 22.6f, 10f, 3f, 1.6f),
            PlanArea(AreaKind.STAIRS, "Escalera", 27.2f, 2.2f, 4.8f, 12f),
            PlanArea(AreaKind.LEVEL_LABEL, "Subsuelo", 1f, 14.8f, 0f, 0f),
            PlanArea(AreaKind.ROOM, null, 6.8f, 16.4f, 19.6f, 5f),
            PlanArea(AreaKind.EQUIPMENT, "UPS", 8f, 17f, 5f, 1.6f),
            PlanArea(AreaKind.EQUIPMENT, "Baterías", 14f, 17f, 5f, 1.6f),
            PlanArea(AreaKind.EQUIPMENT, "Tablero", 20f, 17f, 5.6f, 1.6f),
            PlanArea(AreaKind.STAIRS, null, 27.2f, 16.4f, 4.8f, 5f)
        ),
        checkpoints = listOf(
            Checkpoint("CP1", 1, "Pasillo caliente A", Level.GROUND_FLOOR, CheckpointZone.HOT_AISLE, PlanPoint(15.2f, 3.6f)),
            Checkpoint("CP2", 2, "Climatización", Level.GROUND_FLOOR, CheckpointZone.COOLING, PlanPoint(21.8f, 8.2f)),
            Checkpoint("CP3", 3, "Pasillo frío", Level.GROUND_FLOOR, CheckpointZone.COLD_AISLE, PlanPoint(15.2f, 8.2f)),
            Checkpoint("CP4", 4, "Pasillo caliente B", Level.GROUND_FLOOR, CheckpointZone.HOT_AISLE, PlanPoint(15.2f, 12.8f)),
            Checkpoint("CP5", 5, "Rack de red", Level.GROUND_FLOOR, CheckpointZone.NETWORK, PlanPoint(24.1f, 12.8f)),
            Checkpoint("CP6", 6, "Tablero general", Level.BASEMENT, CheckpointZone.ELECTRICAL, PlanPoint(22.8f, 20.2f)),
            Checkpoint("CP7", 7, "Baterías", Level.BASEMENT, CheckpointZone.ELECTRICAL, PlanPoint(16.5f, 20.2f)),
            Checkpoint("CP8", 8, "UPS", Level.BASEMENT, CheckpointZone.ELECTRICAL, PlanPoint(10.5f, 20.2f))
        ),
        route = listOf(
            PlanPoint(3.3f, 12.6f),
            PlanPoint(6.2f, 12.6f),
            PlanPoint(6.2f, 3.6f),
            PlanPoint(21.8f, 3.6f),
            PlanPoint(21.8f, 8.2f),
            PlanPoint(8f, 8.2f),
            PlanPoint(8f, 12.8f),
            PlanPoint(29.6f, 12.8f),
            PlanPoint(29.6f, 20.2f),
            PlanPoint(10.5f, 20.2f)
        ),
        missions = listOf(
            MissionPlan(MissionType.FULL_ROUND, listOf("CP1", "CP2", "CP3", "CP4", "CP5", "CP6", "CP7", "CP8")),
            MissionPlan(MissionType.GROUND_FLOOR_ROUND, listOf("CP1", "CP2", "CP3", "CP4", "CP5")),
            MissionPlan(MissionType.SPOT_CHECK, emptyList())
        ),
        thresholds = Thresholds(
            batteryMinToStartPct = 40,
            batteryWarningPct = 30,
            batteryCriticalPct = 15,
            staleAfterSeconds = 5,
            degradedLatencyMs = 1000,
            commandTimeoutSeconds = 5,
            temperature = mapOf(
                CheckpointZone.COLD_AISLE to Limits(27.0, 32.0),
                CheckpointZone.HOT_AISLE to Limits(38.0, 45.0),
                CheckpointZone.COOLING to Limits(25.0, 30.0),
                CheckpointZone.NETWORK to Limits(30.0, 35.0),
                CheckpointZone.ELECTRICAL to Limits(30.0, 35.0)
            ),
            humidityLow = Limits(20.0, 10.0),
            humidityHigh = Limits(70.0, 80.0),
            noiseAboveBaseline = Limits(10.0, 20.0)
        )
    )

    /** Tramo de la escalera dentro del recorrido (índice del segmento vertical entre niveles). */
    const val STAIRS_SEGMENT_INDEX = 7
}
