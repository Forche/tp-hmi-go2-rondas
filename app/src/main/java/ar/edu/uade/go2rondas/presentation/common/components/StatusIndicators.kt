package ar.edu.uade.go2rondas.presentation.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.theme.statusColors
import ar.edu.uade.go2rondas.domain.model.CommandRecord
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.presentation.common.labelRes

/** Etiqueta redondeada con color de fondo y de texto. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        // clip antes que background: si no, el fondo se pinta sin redondear.
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Estado operativo del robot. Sin conexión tiene prioridad: el estado informado puede ser viejo. */
@Composable
fun StatusChip(state: OperationalState?, connected: Boolean, modifier: Modifier = Modifier) {
    val status = MaterialTheme.statusColors
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        !connected || state == null -> status.staleContainer to status.stale
        state == OperationalState.AT_BASE -> scheme.surfaceVariant to scheme.onSurfaceVariant
        state == OperationalState.ON_MISSION || state == OperationalState.RETURNING -> scheme.primaryContainer to scheme.primary
        state == OperationalState.PAUSED -> status.staleContainer to status.stale
        state == OperationalState.AWAITING_DECISION -> status.warningContainer to status.warning
        else -> status.errorContainer to status.error
    }
    val label = if (!connected || state == null) R.string.state_no_connection else state.labelRes()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(content, CircleShape))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Barras de señal + texto. El color nunca va solo: siempre lleva la palabra. */
@Composable
fun ConnectionIndicator(state: LinkState, modifier: Modifier = Modifier) {
    val status = MaterialTheme.statusColors
    val (color, activeBars) = when (state) {
        LinkState.CONNECTED -> status.ok to 3
        LinkState.DEGRADED -> status.warning to 2
        LinkState.DISCONNECTED -> status.error to 1
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(Modifier.size(16.dp)) {
            val barWidth = size.width / 5
            repeat(3) { i ->
                val height = size.height * (0.35f + 0.3f * i)
                drawRoundRect(
                    color = if (i < activeBars) color else status.mapRack,
                    topLeft = Offset(i * barWidth * 2, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }
        Text(stringResource(state.labelRes()), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun BatteryIndicator(percent: Int?, stale: Boolean, modifier: Modifier = Modifier) {
    val status = MaterialTheme.statusColors
    val fill = when {
        stale || percent == null -> status.stale
        percent < 15 -> status.error
        percent < 30 -> status.warning
        else -> status.ok
    }
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
            val body = Size(size.width - 3.dp.toPx(), size.height)
            drawRoundRect(outline, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            val inset = 2.5.dp.toPx()
            val level = ((percent ?: 0) / 100f).coerceIn(0f, 1f)
            drawRoundRect(
                color = fill,
                topLeft = Offset(inset, inset),
                size = Size((body.width - inset * 2) * level, body.height - inset * 2),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            drawRoundRect(
                color = outline,
                topLeft = Offset(size.width - 2.dp.toPx(), size.height * 0.3f),
                size = Size(2.dp.toPx(), size.height * 0.4f),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        Text(
            text = percent?.let { stringResource(R.string.battery_percent, it) } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (stale) status.stale else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Estado visible de la última orden: pendiente, confirmada, rechazada o sin respuesta. */
@Composable
fun CommandStatusPill(record: CommandRecord, modifier: Modifier = Modifier) {
    val status = MaterialTheme.statusColors
    val scheme = MaterialTheme.colorScheme
    data class Look(val container: Color, val content: Color, @param:DrawableRes val icon: Int)
    val look = when (record.status) {
        CommandStatus.PENDING -> Look(scheme.surfaceVariant, scheme.onSurfaceVariant, R.drawable.ic_clock)
        CommandStatus.CONFIRMED -> Look(status.okContainer, status.ok, R.drawable.ic_check_circle)
        CommandStatus.REJECTED -> Look(status.errorContainer, status.error, R.drawable.ic_cancel_circle)
        CommandStatus.NO_RESPONSE -> Look(status.warningContainer, status.warning, R.drawable.ic_error_circle)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(look.container)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painterResource(look.icon), contentDescription = null, tint = look.content, modifier = Modifier.size(16.dp))
        Text(stringResource(record.command.type.labelRes()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        Text(stringResource(record.status.labelRes()), style = MaterialTheme.typography.bodySmall, color = look.content)
    }
}
