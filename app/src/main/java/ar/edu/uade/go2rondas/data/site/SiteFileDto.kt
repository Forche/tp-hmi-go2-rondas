package ar.edu.uade.go2rondas.data.site

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Archivo de sitio (formato go2-sitio/1, ver sitios/README.md). Es el mismo archivo que carga el
// servidor MuJoCo. Los datos de la escena 3D ("scene_3d", "color", "height_m") la app los ignora.

@Serializable
data class SiteFileDto(
    val format: String,
    @SerialName("site_id") val siteId: String,
    val name: String,
    val width: Float,
    val height: Float,
    val base: PointDto,
    @SerialName("basement_start_y") val basementStartY: Float? = null,
    val stairs: StairsDto? = null,
    val areas: List<AreaDto> = emptyList(),
    val checkpoints: List<CheckpointDto>,
    val route: List<PointDto>,
    val missions: List<MissionDto>,
    val thresholds: ThresholdsDto,
    /** Sólo para el robot simulado: un robot real no lo manda. */
    val simulation: SimulationDto? = null
)

@Serializable
data class PointDto(val x: Float, val y: Float)

@Serializable
data class StairsDto(val left: Float, val right: Float, val top: Float, val bottom: Float)

@Serializable
data class AreaDto(
    val kind: String,
    val label: String? = null,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@Serializable
data class CheckpointDto(
    val id: String,
    val number: Int,
    val name: String,
    val zone: String,
    val x: Float,
    val y: Float
)

@Serializable
data class MissionDto(val type: String, val checkpoints: List<String>)

@Serializable
data class LimitsDto(val warning: Double, val critical: Double)

@Serializable
data class BatteryThresholdsDto(
    @SerialName("min_to_start") val minToStart: Int,
    val warning: Int,
    val critical: Int
)

@Serializable
data class ThresholdsDto(
    val battery: BatteryThresholdsDto,
    val temperature: Map<String, LimitsDto>,
    @SerialName("humidity_low") val humidityLow: LimitsDto,
    @SerialName("humidity_high") val humidityHigh: LimitsDto,
    @SerialName("noise_above_baseline") val noiseAboveBaseline: LimitsDto
)

@Serializable
data class SimulationDto(
    @SerialName("humidity_pct") val humidityPct: Double = 45.0,
    @SerialName("typical_readings") val typicalReadings: Map<String, TypicalReadingDto>
)

@Serializable
data class TypicalReadingDto(
    @SerialName("temperature_c") val temperatureC: Double,
    @SerialName("noise_dba") val noiseDba: Double
)
