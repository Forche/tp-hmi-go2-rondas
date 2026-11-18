package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.LiveCameraState
import ar.edu.uade.go2rondas.domain.repository.LiveCameraRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLiveCameraUseCase @Inject constructor(private val repository: LiveCameraRepository) {
    operator fun invoke(): Flow<LiveCameraState> = repository.observe()
}
