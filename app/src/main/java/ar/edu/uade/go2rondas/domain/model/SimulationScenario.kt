package ar.edu.uade.go2rondas.domain.model

/** Situaciones que el modo demo puede forzar en el robot simulado. */
enum class SimulationScenario {
    LOW_BATTERY,
    CONNECTION_LOSS,
    OBSTACLE,
    HIGH_TEMPERATURE,
    REJECT_NEXT_COMMAND,
    IGNORE_NEXT_COMMAND
}
