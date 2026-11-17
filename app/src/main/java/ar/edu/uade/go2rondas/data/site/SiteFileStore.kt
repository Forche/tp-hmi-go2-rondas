package ar.edu.uade.go2rondas.data.site

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Archivos de sitio guardados en el teléfono:
 * - el que tiene cargado el robot simulado (Go2-01); si nunca se cargó uno, el de ejemplo que viene en el APK;
 * - la última copia del mapa que mandó cada robot remoto, para mostrar el plano aunque el enlace esté caído.
 */
@Singleton
class SiteFileStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.filesDir, "sitios").apply { mkdirs() }

    private fun simulatorFile(robotId: String) = File(dir, "$robotId-cargado.json")
    private fun receivedFile(robotId: String) = File(dir, "$robotId-recibido.json")

    /** Sitio de ejemplo que viene en el APK (la carpeta sitios/ del repositorio). */
    fun defaultSite(): SiteDefinition =
        SiteFile.parse(context.assets.open(DEFAULT_ASSET).bufferedReader().use { it.readText() }, requireSimulation = true)

    fun loadSimulatorSite(robotId: String): SiteDefinition {
        val file = simulatorFile(robotId)
        if (file.exists()) {
            try {
                return SiteFile.parse(file.readText(), requireSimulation = true)
            } catch (e: InvalidSiteException) {
                Log.w(TAG, "El sitio guardado ya no es válido; se usa el de ejemplo", e)
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo leer el sitio guardado", e)
            }
        }
        return defaultSite()
    }

    /** Lee el archivo elegido por el operador y, si es válido, lo deja guardado para el robot simulado. */
    fun importSimulatorSite(robotId: String, uri: Uri): SiteDefinition {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: IOException) {
            null
        } ?: throw InvalidSiteException("no se pudo leer el archivo")
        val site = SiteFile.parse(text, requireSimulation = true)
        simulatorFile(robotId).writeText(text)
        return site
    }

    fun resetSimulatorSite(robotId: String): SiteDefinition {
        simulatorFile(robotId).delete()
        return defaultSite()
    }

    fun loadReceivedSite(robotId: String): SiteDefinition? {
        val file = receivedFile(robotId)
        if (!file.exists()) return null
        return try {
            SiteFile.parse(file.readText())
        } catch (e: InvalidSiteException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    fun saveReceivedSite(robotId: String, dto: SiteFileDto) {
        try {
            receivedFile(robotId).writeText(SiteFile.encode(dto))
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo guardar el mapa recibido", e)
        }
    }

    private companion object {
        const val TAG = "SiteFileStore"
        const val DEFAULT_ASSET = "datacenter-edificio-a.json"
    }
}
