package ar.edu.uade.go2rondas.data.remote

import kotlinx.serialization.json.Json

/**
 * Configuración JSON del contrato: ignora campos que la app no conoce (un servidor más nuevo no la
 * rompe) y no escribe los parámetros nulos de las órdenes.
 */
val RobotJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    classDiscriminator = "type"
}
