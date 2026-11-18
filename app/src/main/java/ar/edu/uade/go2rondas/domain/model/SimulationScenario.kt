package ar.edu.uade.go2rondas.domain.model

/** Situaciones que el modo demo puede forzar en el robot simulado. */
enum class SimulationScenario {
    LOW_BATTERY,
    CONNECTION_LOSS,
    OBSTACLE,
    OBSTACLE_PATH,
    WATER_LEAK,
    SMOKE,
    HIGH_TEMPERATURE,
    HUMIDITY_ANOMALY,
    NOISE_ANOMALY,
    REJECT_NEXT_COMMAND,
    IGNORE_NEXT_COMMAND
}
