package ar.edu.uade.go2rondas.presentation.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/** Un solo snackbar para toda la app; cada pantalla lo toma de acá para avisos efímeros. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }
