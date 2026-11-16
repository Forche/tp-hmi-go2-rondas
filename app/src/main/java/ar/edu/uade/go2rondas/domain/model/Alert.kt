package ar.edu.uade.go2rondas.domain.model

data class Alert(
    val id: String,
    val timestampMillis: Long,
    val severity: AlertSeverity,
    val code: AlertCode,
    val checkpointId: String?,
    val measuredValue: Double?,
    val threshold: Double?,
    val requiresDecision: Boolean,
    val decisionOptions: List<IncidentDecision>,
    val acknowledged: Boolean
)

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertCode {
    OBSTACLE,
    TEMPERATURE_HIGH,
    HUMIDITY_OUT_OF_RANGE,
    NOISE_HIGH,
    BATTERY_LOW,
    BATTERY_CRITICAL
}

/** Opciones del operador cuando el robot espera una decisión. */
enum class IncidentDecision { CONTINUE, SKIP, RETRY, WATCH }
