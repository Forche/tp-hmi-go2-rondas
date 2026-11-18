package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Foto de la cámara del robot. El robot nombra la foto de un checkpoint con la ronda y el checkpoint
 * (`<ronda>_<checkpoint>`), así el historial la encuentra sin guardar nada más; la de una alerta viene
 * en la alerta misma.
 */
class ObservePhotoUseCase @Inject constructor(private val repository: PhotoRepository) {

    fun ofCheckpoint(missionId: String, checkpointId: String): Flow<String?> =
        repository.observePhoto("${missionId}_$checkpointId")

    fun ofAlert(alert: Alert?): Flow<String?> =
        alert?.photoId?.let(repository::observePhoto) ?: flowOf(null)
}
