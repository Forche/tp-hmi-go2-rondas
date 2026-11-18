package ar.edu.uade.go2rondas.domain.repository

import kotlinx.coroutines.flow.Flow

/** Fotos de la cámara del robot. Cada una se identifica con el id que manda el robot. */
interface PhotoRepository {
    /** Ruta del archivo JPEG, o null si esa foto no existe (todavía no llegó o el robot no tiene cámara). */
    fun observePhoto(photoId: String): Flow<String?>
}
