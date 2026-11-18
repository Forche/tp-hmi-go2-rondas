package ar.edu.uade.go2rondas.domain.model

/** Cámara en vivo del robot activo. Las imágenes no se guardan: sólo se muestran mientras se mira. */
sealed interface LiveCameraState {
    /** El robot no tiene cámara (el simulador dentro de la app). */
    data object NoCamera : LiveCameraState

    /** Pedida, todavía sin imagen. */
    data object Waiting : LiveCameraState

    /** La última imagen que llegó, en JPEG, con la hora en que la sacó el robot. */
    class Frame(val jpeg: ByteArray, val timestampMillis: Long) : LiveCameraState
}
