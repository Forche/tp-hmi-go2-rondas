package ar.edu.uade.go2rondas.data.repository

import ar.edu.uade.go2rondas.data.photo.PhotoStore
import ar.edu.uade.go2rondas.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PhotoRepositoryImpl @Inject constructor(private val store: PhotoStore) : PhotoRepository {
    override fun observePhoto(photoId: String): Flow<String?> = store.observe(photoId)
}
