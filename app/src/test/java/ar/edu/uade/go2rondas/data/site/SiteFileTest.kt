package ar.edu.uade.go2rondas.data.site

import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SiteFileTest {

    private fun assertInvalid(text: String, expected: String) {
        try {
            SiteFile.parse(text, requireSimulation = true)
            fail("Se esperaba un sitio inválido")
        } catch (e: InvalidSiteException) {
            assertTrue("Mensaje: ${e.message}", e.message.orEmpty().contains(expected))
        }
    }

    @Test
    fun `every example site loads`() {
        assertTrue(TestSites.all().size >= 2)
        TestSites.all().forEach { SiteFile.parse(it.readText(), requireSimulation = true) }
    }

    @Test
    fun `building A has a basement and stairs`() {
        val site = TestSites.load().config
        assertEquals(8, site.checkpoints.size)
        assertEquals(Level.BASEMENT, site.checkpoint("CP6")?.level)
        assertTrue(site.isOnStairs(PlanPoint(29.6f, 16f)))
        assertEquals(listOf(MissionType.FULL_ROUND, MissionType.GROUND_FLOOR_ROUND, MissionType.SPOT_CHECK), site.missions.map { it.type })
    }

    @Test
    fun `building B is a single floor with its own missions`() {
        val site = TestSites.load(TestSites.BUILDING_B).config
        assertNull(site.basementStartY)
        assertEquals(Level.GROUND_FLOOR, site.levelAt(PlanPoint(10f, 13f)))
        assertFalse(site.isOnStairs(PlanPoint(21.6f, 11.2f)))
        assertEquals(listOf(MissionType.FULL_ROUND, MissionType.SPOT_CHECK), site.missions.map { it.type })
    }

    @Test
    fun `the map sent by the robot does not need the simulation data`() {
        val withoutSimulation = TestSites.text().replace("\"simulation\"", "\"_simulation\"")
        val definition = SiteFile.parse(withoutSimulation)
        assertNull(definition.simulation)
        assertInvalid(withoutSimulation, "simulation")
    }

    @Test
    fun `a checkpoint off the route is rejected`() {
        assertInvalid(TestSites.text().replace("\"x\": 15.2, \"y\": 3.6", "\"x\": 15.2, \"y\": 5.6"), "CP1 no está sobre el recorrido")
    }

    @Test
    fun `a mission with an unknown checkpoint is rejected`() {
        assertInvalid(TestSites.text().replace("\"CP4\", \"CP5\"] }", "\"CP4\", \"CP9\"] }"), "CP9")
    }

    @Test
    fun `an unknown zone is rejected`() {
        assertInvalid(TestSites.text().replace("\"zone\": \"NETWORK\"", "\"zone\": \"KITCHEN\""), "KITCHEN")
    }

    @Test
    fun `something that is not a site is rejected`() {
        assertInvalid("{\"hola\": 1}", "no es un archivo de sitio")
        assertInvalid("no es json", "no es un archivo de sitio")
    }
}
