package ar.edu.uade.go2rondas.presentation.common

import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.EnvironmentReading
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.MissionProgress
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Thresholds
import ar.edu.uade.go2rondas.presentation.common.components.CheckpointRowStatus
import ar.edu.uade.go2rondas.presentation.common.components.TelemetryCardState

// Cómo se pinta cada dato según los umbrales del sitio. Sin datos recientes, todo pasa a "antiguo".

fun batteryState(percent: Int, thresholds: Thresholds, stale: Boolean) = when {
    stale -> TelemetryCardState.STALE
    percent < thresholds.batteryCriticalPct -> TelemetryCardState.ERROR
    percent < thresholds.batteryWarningPct -> TelemetryCardState.WARNING
    else -> TelemetryCardState.NORMAL
}

fun temperatureState(reading: EnvironmentReading, site: SiteConfig, stale: Boolean): TelemetryCardState {
    if (stale) return TelemetryCardState.STALE
    val zone = site.checkpoint(reading.checkpointId)?.zone ?: return TelemetryCardState.NORMAL
    val limits = site.thresholds.temperature[zone] ?: return TelemetryCardState.NORMAL
    return when {
        reading.temperatureC >= limits.critical -> TelemetryCardState.ERROR
        reading.temperatureC >= limits.warning -> TelemetryCardState.WARNING
        else -> TelemetryCardState.NORMAL
    }
}

fun humidityState(reading: EnvironmentReading, thresholds: Thresholds, stale: Boolean): TelemetryCardState {
    if (stale) return TelemetryCardState.STALE
    val value = reading.humidityPct
    return when {
        value <= thresholds.humidityLow.critical || value >= thresholds.humidityHigh.critical -> TelemetryCardState.ERROR
        value <= thresholds.humidityLow.warning || value >= thresholds.humidityHigh.warning -> TelemetryCardState.WARNING
        else -> TelemetryCardState.NORMAL
    }
}

fun noiseState(reading: EnvironmentReading, thresholds: Thresholds, stale: Boolean): TelemetryCardState {
    if (stale) return TelemetryCardState.STALE
    val excess = reading.noiseDba - reading.noiseBaselineDba
    return when {
        excess >= thresholds.noiseAboveBaseline.critical -> TelemetryCardState.ERROR
        excess >= thresholds.noiseAboveBaseline.warning -> TelemetryCardState.WARNING
        else -> TelemetryCardState.NORMAL
    }
}

fun linkState(state: LinkState) = when (state) {
    LinkState.CONNECTED -> TelemetryCardState.NORMAL
    LinkState.DEGRADED -> TelemetryCardState.WARNING
    LinkState.DISCONNECTED -> TelemetryCardState.ERROR
}

fun SiteConfig.temperatureLimit(checkpointId: String): Double? =
    checkpoint(checkpointId)?.zone?.let { thresholds.temperature[it]?.warning }

/** Estado de cada checkpoint de la ronda actual, a partir de los resultados guardados y del progreso. */
fun checkpointStatuses(
    results: List<CheckpointResult>,
    mission: MissionProgress?
): (String) -> CheckpointRowStatus {
    val byCheckpoint = results.associateBy { it.checkpointId }
    return { id ->
        when (byCheckpoint[id]?.status) {
            CheckpointStatus.OK -> CheckpointRowStatus.OK
            CheckpointStatus.ANOMALY -> CheckpointRowStatus.ANOMALY
            CheckpointStatus.SKIPPED -> CheckpointRowStatus.SKIPPED
            null -> if (mission?.currentCheckpointId == id) CheckpointRowStatus.CURRENT else CheckpointRowStatus.PENDING
        }
    }
}
