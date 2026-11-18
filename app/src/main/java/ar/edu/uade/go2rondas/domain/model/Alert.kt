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
    val acknowledged: Boolean,
    /** Foto de la cámara del robot al momento de la alerta; null si el robot no tiene cámara. */
    val photoId: String? = null
)

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertCode {
    OBSTACLE,
    TEMPERATURE_HIGH,
    HUMIDITY_OUT_OF_RANGE,
    NOISE_HIGH,
    BATTERY_LOW,
    BATTERY_CRITICAL,

    /** Regla R5: la batería no alcanza para inspeccionar el subsuelo y volver; el robot no baja. */
    BATTERY_INSUFFICIENT_FOR_BASEMENT
}

/** Opciones del operador cuando el robot espera una decisión. */
enum class IncidentDecision { CONTINUE, SKIP, RETRY, WATCH }
