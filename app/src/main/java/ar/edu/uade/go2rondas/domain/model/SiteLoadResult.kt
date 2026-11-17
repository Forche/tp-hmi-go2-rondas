package ar.edu.uade.go2rondas.domain.model

/** Resultado de cargarle un archivo de sitio al robot simulado. */
sealed interface SiteLoadResult {
    data class Loaded(val siteName: String) : SiteLoadResult

    /** El archivo no sirve; [reason] dice por qué y el robot sigue con el sitio anterior. */
    data class Invalid(val reason: String) : SiteLoadResult

    /** Sólo se cambia de sitio con el robot en la base. */
    data object RobotBusy : SiteLoadResult
}
