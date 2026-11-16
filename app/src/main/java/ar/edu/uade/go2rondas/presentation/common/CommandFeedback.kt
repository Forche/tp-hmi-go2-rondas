package ar.edu.uade.go2rondas.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.RejectionReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Aviso efímero sobre una orden que no se pudo cumplir. El estado durable (la última orden y su
 * resultado) está en el repositorio; esto sólo dispara el snackbar.
 */
sealed interface CommandFeedback {
    data class Rejected(val type: CommandType, val reason: RejectionReason) : CommandFeedback
    data class NoResponse(val type: CommandType) : CommandFeedback
}

suspend fun MutableSharedFlow<CommandFeedback>.report(type: CommandType, outcome: CommandOutcome) {
    when (outcome) {
        CommandOutcome.Confirmed -> Unit
        is CommandOutcome.Rejected -> emit(CommandFeedback.Rejected(type, outcome.reason))
        CommandOutcome.NoResponse -> emit(CommandFeedback.NoResponse(type))
    }
}

/** Muestra en el snackbar los avisos de [feedback] mientras la pantalla está en composición. */
@Composable
fun CommandFeedbackEffect(feedback: Flow<CommandFeedback>) {
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(feedback) {
        feedback.collect { item ->
            val message = when (item) {
                is CommandFeedback.Rejected -> context.getString(
                    R.string.msg_command_rejected,
                    context.getString(item.type.labelRes()),
                    context.getString(item.reason.labelRes())
                )
                is CommandFeedback.NoResponse -> context.getString(
                    R.string.msg_command_no_response,
                    context.getString(item.type.labelRes())
                )
            }
            snackbar.showSnackbar(message)
        }
    }
}

/** Para los botones deshabilitados: muestra el motivo en el snackbar. */
@Composable
fun rememberMessageShower(): (Int) -> Unit {
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(snackbar, context, scope) {
        { resId -> scope.launch { snackbar.showSnackbar(context.getString(resId)) } }
    }
}
