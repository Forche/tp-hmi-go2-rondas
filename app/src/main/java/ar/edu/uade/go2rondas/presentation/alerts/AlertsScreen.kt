package ar.edu.uade.go2rondas.presentation.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.presentation.common.checkpointLabel
import ar.edu.uade.go2rondas.presentation.common.components.AlertCard
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.rememberNow

@Composable
fun AlertsScreen(
    onOpenAlert: (String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = rememberNow()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.alerts_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> AlertList(current.data, onOpenAlert)
        }
    }
}

@Composable
private fun AlertList(data: AlertsData, onOpenAlert: (String) -> Unit) {
    val alerts = data.snapshot.alerts
    val pending = data.snapshot.unacknowledgedAlerts.size
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = when {
                    alerts.isEmpty() -> stringResource(R.string.alerts_empty)
                    pending > 0 -> stringResource(R.string.alerts_unacknowledged_count, pending)
                    else -> stringResource(R.string.alerts_all_acknowledged)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(alerts, key = { it.id }) { alert ->
            AlertCard(
                alert = alert,
                checkpointLabel = checkpointLabel(data.site, alert.checkpointId),
                onClick = { onOpenAlert(alert.id) }
            )
        }
    }
}
