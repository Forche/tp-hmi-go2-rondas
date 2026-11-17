package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.data.site.TestSites
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePathTest {

    // Una "L": 10 m hacia el este y 5 m hacia el sur.
    private val path = RoutePath(listOf(PlanPoint(0f, 0f), PlanPoint(10f, 0f), PlanPoint(10f, 5f)))

    @Test
    fun `length is the sum of the segments`() {
        assertEquals(15.0, path.length, 1e-6)
    }

    @Test
    fun `point at a distance follows the polyline`() {
        assertEquals(PlanPoint(4f, 0f), path.pointAt(4.0))
        assertEquals(PlanPoint(10f, 3f), path.pointAt(13.0))
    }

    @Test
    fun `distances outside the path are clamped`() {
        assertEquals(PlanPoint(0f, 0f), path.pointAt(-3.0))
        assertEquals(PlanPoint(10f, 5f), path.pointAt(99.0))
    }

    @Test
    fun `distance of a point projects it onto the closest segment`() {
        assertEquals(6.0, path.distanceOf(PlanPoint(6f, 0.4f)), 1e-4)
        assertEquals(12.0, path.distanceOf(PlanPoint(10.3f, 2f)), 1e-4)
    }

    @Test
    fun `heading is reversed when going back`() {
        assertEquals(0, path.headingAt(2.0, forward = true))
        assertEquals(180, path.headingAt(2.0, forward = false))
        assertEquals(90, path.headingAt(12.0, forward = true))
    }

    @Test
    fun `every checkpoint of the example sites lies on the route in mission order`() = listOf(
        TestSites.BUILDING_A, TestSites.BUILDING_B
    ).forEach { name ->
        val site = TestSites.load(name).config
        val route = RoutePath(site.route)
        val distances = site.checkpoints.map { route.distanceOf(it.position) }
        assertEquals(distances.sorted(), distances)
        site.checkpoints.forEach { cp ->
            val onRoute = route.pointAt(route.distanceOf(cp.position))
            assertTrue("${cp.id} fuera del recorrido", kotlin.math.hypot(onRoute.x - cp.position.x, onRoute.y - cp.position.y) < 0.01f)
        }
    }
}
