package ar.edu.uade.go2rondas.presentation.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.presentation.common.components.ButtonKind
import ar.edu.uade.go2rondas.presentation.common.components.CommandButton
import ar.edu.uade.go2rondas.presentation.common.components.CommandStatusPill
import ar.edu.uade.go2rondas.presentation.common.components.HoldToStopButton
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.rememberMessageShower

/** Hoja de controles: todas las órdenes de la ronda con su habilitación y la detención operativa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlSheet(snapshot: RobotSnapshot, actions: PanelActions) {
    val availability = snapshot.availability
    val showMessage = rememberMessageShower()

    @Composable
    fun Command(type: CommandType, kind: ButtonKind, onClick: () -> Unit) {
        CommandButton(
            text = stringResource(type.labelRes()),
            kind = kind,
            enabled = availability.isEnabled(type),
            onClick = onClick,
            onDisabledClick = { availability.reason(type)?.let { showMessage(it.labelRes()) } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = actions.onCloseControls,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.controls_title), style = MaterialTheme.typography.titleMedium)
            snapshot.lastCommand?.let { record ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.controls_last_command),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CommandStatusPill(record)
                }
            }
            Command(CommandType.PAUSE, ButtonKind.SECONDARY, actions.onPause)
            Command(CommandType.RESUME, ButtonKind.SECONDARY, actions.onResume)
            Command(CommandType.RETURN_TO_BASE, ButtonKind.SECONDARY, actions.onReturnToBase)
            Command(CommandType.CANCEL, ButtonKind.DANGER, actions.onRequestCancel)
            HoldToStopButton(
                enabled = availability.isEnabled(CommandType.OPERATIONAL_STOP),
                onStop = actions.onOperationalStop
            )
            Text(
                stringResource(R.string.controls_stop_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CancelMissionDialog(actions: PanelActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissCancel,
        title = { Text(stringResource(R.string.cancel_dialog_title)) },
        text = { Text(stringResource(R.string.cancel_dialog_text)) },
        confirmButton = {
            TextButton(onClick = actions.onConfirmCancel) {
                Text(stringResource(R.string.command_cancel), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissCancel) { Text(stringResource(R.string.cancel_dialog_dismiss)) }
        }
    )
}
