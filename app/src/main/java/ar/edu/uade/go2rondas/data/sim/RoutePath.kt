package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.domain.model.PlanPoint
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Polilínea del recorrido. El simulador ubica al robot por la distancia recorrida
 * desde la base: avanzar es sumar distancia y volver a la base es restarla.
 */
class RoutePath(private val points: List<PlanPoint>) {

    private val cumulative: List<Double> = points.zipWithNext()
        .runningFold(0.0) { acc, (a, b) -> acc + hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }

    val length: Double get() = cumulative.last()

    fun segmentIndexAt(distance: Double): Int {
        val d = distance.coerceIn(0.0, length)
        val index = cumulative.indexOfFirst { it >= d } - 1
        return index.coerceIn(0, points.size - 2)
    }

    fun pointAt(distance: Double): PlanPoint {
        val i = segmentIndexAt(distance)
        val a = points[i]
        val b = points[i + 1]
        val segmentLength = cumulative[i + 1] - cumulative[i]
        val t = if (segmentLength == 0.0) 0.0 else ((distance.coerceIn(0.0, length) - cumulative[i]) / segmentLength)
        return PlanPoint(
            x = (a.x + (b.x - a.x) * t).toFloat(),
            y = (a.y + (b.y - a.y) * t).toFloat()
        )
    }

    /** Rumbo en grados (0 = este, 90 = sur en el plano), según el sentido de avance. */
    fun headingAt(distance: Double, forward: Boolean): Int {
        val i = segmentIndexAt(distance)
        val a = points[i]
        val b = points[i + 1]
        val deg = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))
        val directed = if (forward) deg else deg + 180
        return ((directed % 360 + 360) % 360).roundToInt()
    }

    /** Distancia desde la base hasta el punto del recorrido más cercano a [point]. */
    fun distanceOf(point: PlanPoint): Double = closest(point).first

    /** A cuántos metros del recorrido queda [point]. */
    fun gapTo(point: PlanPoint): Double = closest(point).second

    private fun closest(point: PlanPoint): Pair<Double, Double> {
        var best = 0.0
        var bestGap = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else
                (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
            val px = a.x + dx * t
            val py = a.y + dy * t
            val gap = hypot(point.x - px, point.y - py)
            if (gap < bestGap) {
                bestGap = gap
                best = cumulative[i] + t * hypot(dx, dy)
            }
        }
        return best to bestGap
    }
}
