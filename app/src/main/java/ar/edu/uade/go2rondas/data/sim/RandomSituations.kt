package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import kotlin.random.Random

/**
 * Situaciones aleatorias: para que una ronda de prueba se parezca más a una noche real. Mientras el robot
 * está en ronda, cada 45 s a 2 min de tiempo simulado pasa algo al azar. La batería baja y el humo
 * terminan la ronda, así que pasan como mucho una vez por ronda. Mismo criterio que el servidor MuJoCo.
 */
class RandomSituations(private val random: Random = Random.Default) {

    private var missionId: String? = null
    private val used = mutableSetOf<SimulationScenario>()
    private var countdown = nextWait()

    /** Pasaron [dt] segundos simulados con el robot en ronda. Devuelve la situación que toca, o null. */
    fun tick(dt: Double, currentMissionId: String): SimulationScenario? {
        if (currentMissionId != missionId) {
            missionId = currentMissionId
            used.clear()
            countdown = nextWait()
        }
        countdown -= dt
        if (countdown > 0) return null
        countdown = nextWait()
        val options = WEIGHTS.filterKeys { it !in used }
        var pick = random.nextInt(options.values.sum())
        val scenario = options.entries.first { (_, weight) -> (pick - weight).also { pick = it } < 0 }.key
        if (scenario in ONCE_PER_ROUND) used += scenario
        return scenario
    }

    private fun nextWait() = random.nextDouble(MIN_SECONDS, MAX_SECONDS)

    private companion object {
        const val MIN_SECONDS = 45.0
        const val MAX_SECONDS = 120.0
        val WEIGHTS = linkedMapOf(
            SimulationScenario.HIGH_TEMPERATURE to 3,
            SimulationScenario.HUMIDITY_ANOMALY to 3,
            SimulationScenario.NOISE_ANOMALY to 3,
            SimulationScenario.OBSTACLE to 3,
            SimulationScenario.WATER_LEAK to 2,
            SimulationScenario.CONNECTION_LOSS to 1,
            SimulationScenario.LOW_BATTERY to 1,
            SimulationScenario.SMOKE to 1
        )
        val ONCE_PER_ROUND = setOf(SimulationScenario.LOW_BATTERY, SimulationScenario.SMOKE)
    }
}
