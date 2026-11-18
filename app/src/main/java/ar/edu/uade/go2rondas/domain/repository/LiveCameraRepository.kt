package ar.edu.uade.go2rondas.domain.repository

import ar.edu.uade.go2rondas.domain.model.LiveCameraState
import kotlinx.coroutines.flow.Flow

/** Cámara en vivo del robot activo. Mientras alguien observa el flujo, el robot manda imágenes. */
interface LiveCameraRepository {
    fun observe(): Flow<LiveCameraState>
}
