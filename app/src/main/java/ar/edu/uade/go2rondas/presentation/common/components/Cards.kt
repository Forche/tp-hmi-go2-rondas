package ar.edu.uade.go2rondas.presentation.common.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.theme.statusColors
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.presentation.common.formatClock
import ar.edu.uade.go2rondas.presentation.common.formatDateTime
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.nameRes
import ar.edu.uade.go2rondas.presentation.common.titleRes

private val CardShape = RoundedCornerShape(12.dp)

/** Fondo, borde y bordes redondeados en el orden correcto (clip → background → border). */
private fun Modifier.card(container: Color, border: Color, borderWidth: Int = 1): Modifier =
    clip(CardShape).background(container).border(borderWidth.dp, border, CardShape)

// ---------------------------------------------------------------- telemetría

enum class TelemetryCardState { NORMAL, WARNING, ERROR, STALE }

@Composable
fun TelemetryCard(
    label: String,
    value: String,
    detail: String,
    state: TelemetryCardState,
    modifier: Modifier = Modifier
) {
    val status = MaterialTheme.statusColors
    val scheme = MaterialTheme.colorScheme
    data class Look(val container: Color, val accent: Color, val value: Color, @param:DrawableRes val icon: Int?)
    val look = when (state) {
        TelemetryCardState.NORMAL -> Look(scheme.surface, scheme.outline, scheme.onSurface, null)
        TelemetryCardState.WARNING -> Look(status.warningContainer, status.warning, status.warning, R.drawable.ic_warning)
        TelemetryCardState.ERROR -> Look(status.errorContainer, status.error, status.error, R.drawable.ic_error_circle)
        TelemetryCardState.STALE -> Look(status.staleContainer, status.stale, status.stale, R.drawable.ic_clock)
    }
    Column(
        modifier = modifier.card(look.container, look.accent).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            look.icon?.let { Icon(painterResource(it), contentDescription = null, tint = look.accent, modifier = Modifier.size(16.dp)) }
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, color = look.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- alertas

@Composable
fun AlertSeverity.accent(): Color = when (this) {
    AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
    AlertSeverity.WARNING -> MaterialTheme.statusColors.warning
    AlertSeverity.CRITICAL -> MaterialTheme.statusColors.error
}

@Composable
fun AlertSeverity.container(): Color = when (this) {
    AlertSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
    AlertSeverity.WARNING -> MaterialTheme.statusColors.warningContainer
    AlertSeverity.CRITICAL -> MaterialTheme.statusColors.errorContainer
}

@DrawableRes
fun AlertSeverity.iconRes(): Int = when (this) {
    AlertSeverity.INFO -> R.drawable.ic_info
    AlertSeverity.WARNING -> R.drawable.ic_warning
    AlertSeverity.CRITICAL -> R.drawable.ic_error_circle
}

/** Detalle de una alerta: dónde y con qué valor frente al umbral. */
@Composable
fun alertDetailText(alert: Alert, checkpointLabel: String?): String =
    alertDetailText(LocalContext.current, alert, checkpointLabel)

/** Igual que la versión de Compose; la usan también las notificaciones. */
fun alertDetailText(context: Context, alert: Alert, checkpointLabel: String?): String {
    val place = checkpointLabel ?: alert.checkpointId.orEmpty()
    val value = alert.measuredValue ?: 0.0
    val threshold = alert.threshold ?: 0.0
    return when (alert.code) {
        AlertCode.OBSTACLE -> context.getString(R.string.alert_detail_obstacle, place)
        AlertCode.TEMPERATURE_HIGH -> context.getString(R.string.alert_detail_temperature, place, value, threshold)
        AlertCode.HUMIDITY_OUT_OF_RANGE -> context.getString(R.string.alert_detail_humidity, place, value, threshold)
        AlertCode.NOISE_HIGH -> context.getString(R.string.alert_detail_noise, place, value, threshold)
        AlertCode.BATTERY_LOW, AlertCode.BATTERY_CRITICAL -> context.getString(R.string.alert_detail_battery, value, threshold)
        AlertCode.BATTERY_INSUFFICIENT_FOR_BASEMENT -> context.getString(R.string.alert_detail_battery_basement, value, threshold)
        AlertCode.WATER_DETECTED -> context.getString(R.string.alert_detail_water, place)
        AlertCode.SMOKE_DETECTED -> context.getString(R.string.alert_detail_smoke, place)
    }
}

@Composable
fun AlertCard(
    alert: Alert,
    checkpointLabel: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pending = !alert.acknowledged
    val accent = alert.severity.accent()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(if (pending) alert.severity.container() else scheme.surface, if (pending) accent else scheme.outline)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painterResource(alert.severity.iconRes()),
            contentDescription = stringResource(alert.severity.labelRes()),
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(alert.code.titleRes()), style = MaterialTheme.typography.labelLarge)
            Text(alertDetailText(alert, checkpointLabel), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatClock(alert.timestampMillis), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (pending) {
                    Pill(stringResource(R.string.alert_unacknowledged), accent, scheme.onPrimary)
                } else {
                    Pill(stringResource(R.string.alert_acknowledged), scheme.surfaceVariant, scheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- checkpoints

/** Estado de un checkpoint en pantalla (fila de la ronda y marcador del plano). */
enum class CheckpointRowStatus { PENDING, CURRENT, OK, ANOMALY, SKIPPED }

@Composable
fun CheckpointRowStatus.colors(): Pair<Color, Color> {
    val status = MaterialTheme.statusColors
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        CheckpointRowStatus.PENDING -> scheme.surface to scheme.onSurfaceVariant
        CheckpointRowStatus.CURRENT -> scheme.primary to scheme.onPrimary
        CheckpointRowStatus.OK -> status.ok to scheme.onPrimary
        CheckpointRowStatus.ANOMALY -> status.error to scheme.onPrimary
        CheckpointRowStatus.SKIPPED -> status.staleContainer to status.stale
    }
}

@Composable
fun CheckpointRow(
    number: Int,
    name: String,
    detail: String,
    status: CheckpointRowStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (circle, onCircle) = status.colors()
    val scheme = MaterialTheme.colorScheme
    val (label, labelColor) = when (status) {
        CheckpointRowStatus.PENDING -> R.string.checkpoint_pending to scheme.onSurfaceVariant
        CheckpointRowStatus.CURRENT -> R.string.checkpoint_current to scheme.primary
        CheckpointRowStatus.OK -> R.string.checkpoint_ok to MaterialTheme.statusColors.ok
        CheckpointRowStatus.ANOMALY -> R.string.checkpoint_anomaly to MaterialTheme.statusColors.error
        CheckpointRowStatus.SKIPPED -> R.string.checkpoint_skipped to MaterialTheme.statusColors.stale
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(circle)
                .then(if (status == CheckpointRowStatus.PENDING) Modifier.border(1.5.dp, scheme.outline, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = onCircle)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = labelColor)
    }
}

// ---------------------------------------------------------------- historial

@Composable
fun MissionResult?.pillColors(): Pair<Color, Color> {
    val status = MaterialTheme.statusColors
    return when (this) {
        MissionResult.COMPLETED -> status.okContainer to status.ok
        MissionResult.INTERRUPTED_LOW_BATTERY, MissionResult.INTERRUPTED -> status.warningContainer to status.warning
        MissionResult.CANCELLED, MissionResult.ABORTED -> status.staleContainer to status.stale
        null -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
}

@Composable
fun missionSummaryText(record: MissionRecord, includeDate: Boolean = true): String {
    val base = if (includeDate) {
        stringResource(R.string.history_row_detail, formatDateTime(record.startedAtMillis), record.completed, record.total)
    } else {
        stringResource(R.string.history_row_counts, record.completed, record.total)
    }
    val extras = buildList {
        if (record.anomalies > 0) add(stringResource(R.string.history_anomalies, record.anomalies))
        if (record.skipped > 0) add(stringResource(R.string.history_skipped, record.skipped))
        if (isEmpty() && record.result != null) add(stringResource(R.string.history_no_anomalies))
    }
    return (listOf(base) + extras).joinToString(" · ")
}

@Composable
fun HistoryRow(record: MissionRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (container, content) = record.result.pillColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(record.type.nameRes()), style = MaterialTheme.typography.labelLarge)
            Text(missionSummaryText(record), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Pill(stringResource(record.result.labelRes()), container, content)
    }
}

// ---------------------------------------------------------------- selección

@Composable
fun SelectableCard(
    title: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(
                container = if (selected) scheme.primaryContainer else scheme.surface,
                border = if (selected) scheme.primary else scheme.outline,
                borderWidth = if (selected) 2 else 1
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) scheme.onSurface else MaterialTheme.statusColors.disabled)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = scheme.primary)
        }
    }
}
