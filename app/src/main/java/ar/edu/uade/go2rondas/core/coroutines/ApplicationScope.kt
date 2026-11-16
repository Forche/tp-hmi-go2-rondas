package ar.edu.uade.go2rondas.core.coroutines

import javax.inject.Qualifier

/** Scope que vive lo mismo que la app: para el robot y el registro del historial, que no dependen de una pantalla. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
