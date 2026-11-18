package ar.edu.uade.go2rondas.data.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fotos de la cámara frontal que manda el robot (Go2-02, servidor MuJoCo): un JPEG por checkpoint
 * inspeccionado y por obstáculo detectado. Se guardan en la memoria de la app para verlas en el
 * historial aunque el robot ya no esté conectado. El simulador local (Go2-01) no tiene cámara.
 */
@Singleton
class PhotoStore @Inject constructor(@param:ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "fotos")
    private val saved = MutableStateFlow(dir.list()?.map { it.removeSuffix(EXTENSION) }?.toSet() ?: emptySet())

    fun save(photoId: String, jpeg: ByteArray): Boolean {
        val name = fileName(photoId) ?: return false
        return try {
            dir.mkdirs()
            val target = File(dir, name + EXTENSION)
            // Primero a un temporal: quien la esté mirando nunca ve un archivo a medio escribir.
            val partial = File(dir, "$name.tmp")
            partial.writeBytes(jpeg)
            if (!partial.renameTo(target)) {
                target.delete()
                partial.renameTo(target)
            }
            saved.update { it + name }
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Ruta del archivo de la foto, o null mientras no haya llegado. */
    fun observe(photoId: String): Flow<String?> {
        val name = fileName(photoId)
        return saved.map { ids -> if (name != null && name in ids) File(dir, name + EXTENSION).absolutePath else null }
            .distinctUntilChanged()
    }

    /** El id viene del robot: sólo se aceptan letras, números, guion y guion bajo (nada de rutas). */
    private fun fileName(photoId: String): String? =
        photoId.takeIf { it.isNotEmpty() && it.length <= 120 && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }

    private companion object {
        const val EXTENSION = ".jpg"
    }
}
