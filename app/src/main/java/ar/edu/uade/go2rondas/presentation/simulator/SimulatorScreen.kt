package ar.edu.uade.go2rondas.presentation.simulator

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.presentation.common.LocalSnackbarHostState
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.rememberNow

@StringRes
private fun SimulationScenario.titleRes(): Int = when (this) {
    SimulationScenario.LOW_BATTERY -> R.string.scenario_low_battery
    SimulationScenario.CONNECTION_LOSS -> R.string.scenario_connection_loss
    SimulationScenario.OBSTACLE -> R.string.scenario_obstacle
    SimulationScenario.HIGH_TEMPERATURE -> R.string.scenario_high_temperature
    SimulationScenario.REJECT_NEXT_COMMAND -> R.string.scenario_reject
    SimulationScenario.IGNORE_NEXT_COMMAND -> R.string.scenario_ignore
}

@StringRes
private fun SimulationScenario.detailRes(): Int = when (this) {
    SimulationScenario.LOW_BATTERY -> R.string.scenario_low_battery_detail
    SimulationScenario.CONNECTION_LOSS -> R.string.scenario_connection_loss_detail
    SimulationScenario.OBSTACLE -> R.string.scenario_obstacle_detail
    SimulationScenario.HIGH_TEMPERATURE -> R.string.scenario_high_temperature_detail
    SimulationScenario.REJECT_NEXT_COMMAND -> R.string.scenario_reject_detail
    SimulationScenario.IGNORE_NEXT_COMMAND -> R.string.scenario_ignore_detail
}

private val TimeScales = listOf(1, 4, 10)

@Composable
fun SimulatorScreen(onBack: () -> Unit, viewModel: SimulatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SimulatorUiEffect.Triggered -> snackbar.showSnackbar(
                    context.getString(R.string.simulator_triggered, context.getString(effect.scenario.titleRes()))
                )
            }
        }
    }
    val now = rememberNow()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.simulator_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now,
            onBack = onBack
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> SimulatorBody(current.data, viewModel::onTimeScaleSelected, viewModel::onTrigger)
        }
    }
}

@Composable
private fun SimulatorBody(
    data: SimulatorData,
    onTimeScaleSelected: (Int) -> Unit,
    onTrigger: (SimulationScenario) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!data.available) {
            Text(stringResource(R.string.simulator_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(stringResource(R.string.simulator_intro), style = MaterialTheme.typography.bodyMedium)
        Label(stringResource(R.string.simulator_speed))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeScales.forEach { scale ->
                FilterChip(
                    selected = data.timeScale == scale,
                    onClick = { onTimeScaleSelected(scale) },
                    label = { Text(stringResource(R.string.simulator_speed_value, scale)) }
                )
            }
        }
        Label(stringResource(R.string.simulator_scenarios))
        SimulationScenario.entries.forEach { scenario ->
            ScenarioCard(scenario, onTrigger = { onTrigger(scenario) })
        }
    }
}

@Composable
private fun ScenarioCard(scenario: SimulationScenario, onTrigger: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(scenario.titleRes()), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(scenario.detailRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onTrigger) { Text(stringResource(R.string.simulator_trigger)) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
