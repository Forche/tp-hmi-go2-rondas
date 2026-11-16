package ar.edu.uade.go2rondas.presentation.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.theme.statusColors
import ar.edu.uade.go2rondas.domain.model.AreaKind
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import ar.edu.uade.go2rondas.domain.model.SiteConfig

/**
 * Plano esquemático del sitio dibujado desde la configuración (no es el mapa del LiDAR):
 * salas, filas de racks, recorrido, checkpoints con su estado y la posición del robot.
 */
@Composable
fun SiteMap(
    site: SiteConfig,
    checkpointStatus: (checkpointId: String) -> CheckpointRowStatus,
    robotPosition: PlanPoint?,
    robotStale: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val status = MaterialTheme.statusColors
    val scheme = MaterialTheme.colorScheme
    val smallLabel = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 10.sp, color = scheme.onSurfaceVariant)
    val levelLabel = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, color = scheme.onSurfaceVariant)
    val numberLabel = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp)
    // Los colores salen de funciones @Composable: se resuelven acá, fuera del bloque de dibujo.
    val checkpointColors = CheckpointRowStatus.entries.associateWith { it.colors() }
    val baseLabel = stringResource(R.string.map_base)
    val description = stringResource(R.string.cd_site_map)
    val shape = RoundedCornerShape(12.dp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(site.width / site.height)
            .clip(shape)
            .background(status.mapZone)
            .border(1.dp, scheme.outline, shape)
            .semantics { contentDescription = description }
    ) {
        val scale = size.width / site.width
        fun at(point: PlanPoint) = Offset(point.x * scale, point.y * scale)
        val corner = CornerRadius(6.dp.toPx())

        site.areas.forEach { area ->
            val topLeft = Offset(area.left * scale, area.top * scale)
            val areaSize = Size(area.width * scale, area.height * scale)
            val label = area.label
            when (area.kind) {
                AreaKind.ROOM, AreaKind.STAIRS -> {
                    drawRoundRect(scheme.surface, topLeft, areaSize, corner)
                    drawRoundRect(scheme.outline, topLeft, areaSize, corner, style = Stroke(1.dp.toPx()))
                    if (area.kind == AreaKind.STAIRS) {
                        var y = topLeft.y + areaSize.height * 0.45f
                        while (y < topLeft.y + areaSize.height - 4.dp.toPx()) {
                            drawLine(scheme.outline, Offset(topLeft.x + 4.dp.toPx(), y), Offset(topLeft.x + areaSize.width - 4.dp.toPx(), y))
                            y += scale
                        }
                    }
                    if (label != null) drawLabel(textMeasurer, label, topLeft + Offset(4.dp.toPx(), 3.dp.toPx()), smallLabel)
                }
                AreaKind.COLD_AISLE -> drawRect(status.mapColdAisle, topLeft, areaSize)
                AreaKind.RACK_ROW, AreaKind.EQUIPMENT -> {
                    drawRoundRect(status.mapRack, topLeft, areaSize, CornerRadius(3.dp.toPx()))
                    if (label != null) drawCenteredLabel(textMeasurer, label, topLeft + Offset(areaSize.width / 2, areaSize.height / 2), smallLabel)
                }
                AreaKind.LEVEL_LABEL -> if (label != null) drawLabel(textMeasurer, label, topLeft, levelLabel)
            }
        }

        val route = Path().apply {
            site.route.forEachIndexed { i, point ->
                val o = at(point)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        drawPath(
            route,
            color = scheme.primary.copy(alpha = 0.55f),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        )

        val baseSize = Size(4.2f * scale, 2.2f * scale)
        val baseTopLeft = at(site.base) - Offset(baseSize.width / 2, baseSize.height / 2)
        drawRoundRect(scheme.primaryContainer, baseTopLeft, baseSize, corner)
        drawRoundRect(scheme.primary, baseTopLeft, baseSize, corner, style = Stroke(1.5.dp.toPx()))
        drawCenteredLabel(textMeasurer, baseLabel, at(site.base), levelLabel.copy(color = scheme.primary))

        val radius = 1.0f * scale
        site.checkpoints.forEach { checkpoint ->
            val rowStatus = checkpointStatus(checkpoint.id)
            val (fill, onFill) = checkpointColors.getValue(rowStatus)
            val center = at(checkpoint.position)
            drawCircle(fill, radius, center)
            when (rowStatus) {
                CheckpointRowStatus.PENDING -> drawCircle(status.disabled, radius, center, style = Stroke(1.5.dp.toPx()))
                CheckpointRowStatus.SKIPPED -> drawCircle(status.stale, radius, center, style = Stroke(1.5.dp.toPx()))
                else -> Unit
            }
            drawCenteredLabel(textMeasurer, "${checkpoint.number}", center, numberLabel.copy(color = onFill))
        }

        if (robotPosition != null) {
            val center = at(robotPosition)
            val color = if (robotStale) status.stale else scheme.primary
            drawCircle(color.copy(alpha = 0.2f), 14.dp.toPx(), center)
            drawCircle(color, 8.dp.toPx(), center)
            drawCircle(Color.White, 8.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
        }
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, topLeft: Offset, style: TextStyle) {
    drawText(measurer, text, topLeft, style)
}

private fun DrawScope.drawCenteredLabel(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}
