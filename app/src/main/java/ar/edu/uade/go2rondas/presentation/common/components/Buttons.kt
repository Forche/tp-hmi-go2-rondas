package ar.edu.uade.go2rondas.presentation.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.edu.uade.go2rondas.R
import kotlinx.coroutines.launch

enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

/**
 * Botón de una orden. Deshabilitado no recibe toques, así que se le superpone una capa
 * que, al tocarla, muestra por qué no se puede ([onDisabledClick]).
 */
@Composable
fun CommandButton(
    text: String,
    kind: ButtonKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDisabledClick: (() -> Unit)? = null
) {
    Box(modifier) {
        val fill = Modifier.fillMaxWidth()
        when (kind) {
            ButtonKind.PRIMARY -> Button(onClick = onClick, enabled = enabled, modifier = fill) { Text(text) }
            ButtonKind.SECONDARY -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = fill) { Text(text) }
            ButtonKind.DANGER -> Button(
                onClick = onClick,
                enabled = enabled,
                modifier = fill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(text) }
        }
        if (!enabled && onDisabledClick != null) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(50)).clickable(onClick = onDisabledClick))
        }
    }
}

/**
 * Detención operativa: hay que mantenerlo presionado 2 s para evitar toques accidentales (riesgo R2).
 * Soltar antes cancela. Es un comando de software, no una parada de emergencia.
 */
@Composable
fun HoldToStopButton(enabled: Boolean, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val label = when {
        !enabled -> stringResource(R.string.hold_to_stop_disabled)
        progress.value > 0f -> stringResource(R.string.hold_to_stop_pressing)
        else -> stringResource(R.string.hold_to_stop)
    }
    val contentColor = if (enabled) scheme.onError else scheme.onSurfaceVariant
    val description = stringResource(R.string.hold_to_stop)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) scheme.error else scheme.surfaceVariant)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    var completed = false
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(durationMillis = HOLD_MILLIS, easing = LinearEasing))
                        completed = true
                        onStop()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (!completed) {
                        hold.cancel()
                        progress.animateTo(0f, tween(durationMillis = 150))
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Color.Black.copy(alpha = 0.25f))
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

private const val HOLD_MILLIS = 2_000
