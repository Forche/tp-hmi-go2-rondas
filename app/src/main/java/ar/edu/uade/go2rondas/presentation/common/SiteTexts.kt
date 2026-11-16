package ar.edu.uade.go2rondas.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.Battery
import ar.edu.uade.go2rondas.domain.model.EnvironmentReading
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.SiteConfig

/** "CP3 · Pasillo frío". */
@Composable
fun checkpointLabel(site: SiteConfig, checkpointId: String?): String? =
    checkpointId?.let { site.checkpoint(it) }?.let { stringResource(R.string.checkpoint_label, it.id, it.name) }

@Composable
fun checkpointShortName(site: SiteConfig, checkpointId: String?): String =
    checkpointId?.let { site.checkpoint(it)?.name } ?: stringResource(R.string.map_base)

@Composable
fun levelLabel(level: Level): String = stringResource(
    when (level) {
        Level.GROUND_FLOOR -> R.string.level_ground_floor
        Level.BASEMENT -> R.string.level_basement
    }
)

@Composable
fun batteryDetail(battery: Battery): String {
    val hours = battery.estimatedMinutes / 60
    val minutes = battery.estimatedMinutes % 60
    return stringResource(if (battery.charging) R.string.value_charging else R.string.value_autonomy, hours, minutes)
}

@Composable
fun readingDetail(reading: EnvironmentReading): String =
    stringResource(R.string.checkpoint_reading, reading.temperatureC, reading.humidityPct, reading.noiseDba)
