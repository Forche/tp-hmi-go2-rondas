package ar.edu.uade.go2rondas.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val ColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandPrimary,
    background = BgPage,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = BgSurfaceVariant,
    outline = BorderDefault,
    outlineVariant = BorderDefault,
    error = StatusError,
    onError = TextOnPrimary,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusError
)

/** Colores de estado que Material 3 no trae (advertencia, OK, dato antiguo) y los del plano. */
@Immutable
data class StatusColors(
    val ok: Color = StatusOk,
    val okContainer: Color = StatusOkContainer,
    val warning: Color = StatusWarning,
    val warningContainer: Color = StatusWarningContainer,
    val error: Color = StatusError,
    val errorContainer: Color = StatusErrorContainer,
    val stale: Color = StatusStale,
    val staleContainer: Color = StatusStaleContainer,
    val disabled: Color = TextDisabled,
    val mapZone: Color = MapZone,
    val mapRack: Color = MapRack,
    val mapColdAisle: Color = MapColdAisle
)

private val LocalStatusColors = staticCompositionLocalOf { StatusColors() }

/** Acceso como `MaterialTheme.statusColors.warning`, igual que `MaterialTheme.colorScheme`. */
val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

@Composable
fun Go2RondasTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides StatusColors()) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = Typography,
            content = content
        )
    }
}
