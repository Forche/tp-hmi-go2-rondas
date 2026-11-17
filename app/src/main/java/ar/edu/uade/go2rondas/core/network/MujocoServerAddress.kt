package ar.edu.uade.go2rondas.core.network

import javax.inject.Qualifier

/** Dirección "host:puerto" del servidor MuJoCo. Se configura al compilar (`go2.mujocoAddress` en gradle.properties). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MujocoServerAddress
