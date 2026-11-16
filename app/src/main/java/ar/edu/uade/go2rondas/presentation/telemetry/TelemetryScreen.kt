package ar.edu.uade.go2rondas.presentation.telemetry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.presentation.common.batteryDetail
import ar.edu.uade.go2rondas.presentation.common.batteryState
import ar.edu.uade.go2rondas.presentation.common.checkpointLabel
import ar.edu.uade.go2rondas.presentation.common.checkpointShortName
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.components.TelemetryCard
import ar.edu.uade.go2rondas.presentation.common.components.TelemetryCardState
import ar.edu.uade.go2rondas.presentation.common.formatClock
import ar.edu.uade.go2rondas.presentation.common.formatElapsed
import ar.edu.uade.go2rondas.presentation.common.humidityState
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.levelLabel
import ar.edu.uade.go2rondas.presentation.common.linkState
import ar.edu.uade.go2rondas.presentation.common.nameRes
import ar.edu.uade.go2rondas.presentation.common.noiseState
import ar.edu.uade.go2rondas.presentation.common.rememberNow
import ar.edu.uade.go2rondas.presentation.common.temperatureLimit
import ar.edu.uade.go2rondas.presentation.common.temperatureState
import kotlin.math.roundToInt

private data class CardSpec(val label: String, val value: String, val detail: String, val state: TelemetryCardState)

@Composable
fun TelemetryScreen(onBack: () -> Unit, viewModel: TelemetryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = rememberNow()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.telemetry_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now,
            onBack = onBack
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> TelemetryBody(current.data, now)
        }
    }
}

@Composable
private fun TelemetryBody(data: TelemetryData, now: Long) {
    val (snapshot, site) = data
    val telemetry = snapshot.telemetry
    val connection = snapshot.connection
    val stale = connection.state == LinkState.DISCONNECTED
    val dash = stringResource(R.string.value_dash)
    fun ifFresh(state: TelemetryCardState) = if (stale) TelemetryCardState.STALE else state

    val mandatory = buildList {
        add(
            CardSpec(
                stringResource(R.string.tel_connectivity),
                connection.latencyMs?.let { stringResource(R.string.value_latency, it) } ?: dash,
                telemetry?.let { stringResource(R.string.value_rssi, it.link.rssiDbm) } ?: dash,
                linkState(connection.state)
            )
        )
        if (telemetry == null) return@buildList
        add(
            CardSpec(
                stringResource(R.string.tel_battery),
                stringResource(R.string.battery_percent, telemetry.battery.percent),
                batteryDetail(telemetry.battery),
                batteryState(telemetry.battery.percent, site.thresholds, stale)
            )
        )
        val mission = telemetry.mission
        add(
            CardSpec(
                stringResource(R.string.tel_state),
                stringResource(telemetry.state.labelRes()),
                mission?.let { stringResource(it.type.nameRes()) } ?: stringResource(R.string.value_no_mission),
                ifFresh(
                    when (telemetry.state) {
                        OperationalState.AWAITING_DECISION -> TelemetryCardState.WARNING
                        OperationalState.STOPPED -> TelemetryCardState.ERROR
                        else -> TelemetryCardState.NORMAL
                    }
                )
            )
        )
        add(
            CardSpec(
                stringResource(R.string.tel_mission),
                mission?.let { stringResource(R.string.value_mission, it.completed, it.total) } ?: dash,
                mission?.let {
                    stringResource(R.string.value_mission_detail, stringResource(it.type.nameRes()), formatElapsed(it.elapsedSeconds))
                } ?: stringResource(R.string.value_no_mission),
                ifFresh(TelemetryCardState.NORMAL)
            )
        )
        val location = telemetry.location
        add(
            CardSpec(
                stringResource(R.string.tel_location),
                if (location.toCheckpointId == null && location.fromCheckpointId == null) checkpointShortName(site, null)
                else stringResource(
                    R.string.value_segment,
                    location.fromCheckpointId ?: stringResource(R.string.map_base),
                    location.toCheckpointId ?: stringResource(R.string.map_base)
                ),
                stringResource(R.string.value_location_detail, levelLabel(location.level), (location.confidence * 100).roundToInt()),
                ifFresh(if (location.confidence < 0.7) TelemetryCardState.WARNING else TelemetryCardState.NORMAL)
            )
        )
        add(
            CardSpec(
                stringResource(R.string.tel_motion),
                stringResource(R.string.value_speed, telemetry.motion.speedMps),
                if (telemetry.motion.moving) stringResource(R.string.value_heading, location.headingDeg)
                else stringResource(R.string.value_stopped),
                ifFresh(if (telemetry.motion.speedMps > 0.5) TelemetryCardState.WARNING else TelemetryCardState.NORMAL)
            )
        )
        val unacknowledged = snapshot.unacknowledgedAlerts
        add(
            CardSpec(
                stringResource(R.string.tel_alerts),
                "${unacknowledged.size}",
                if (unacknowledged.isEmpty()) stringResource(R.string.value_alerts_none)
                else stringResource(R.string.value_alerts_unacknowledged, unacknowledged.size),
                ifFresh(
                    when {
                        unacknowledged.any { it.severity == AlertSeverity.CRITICAL } -> TelemetryCardState.ERROR
                        unacknowledged.isNotEmpty() -> TelemetryCardState.WARNING
                        else -> TelemetryCardState.NORMAL
                    }
                )
            )
        )
        connection.lastUpdateMillis?.let { last ->
            val seconds = ((now - last) / 1_000).coerceAtLeast(0)
            add(
                CardSpec(
                    stringResource(R.string.tel_last_update),
                    stringResource(R.string.value_seconds_ago, seconds.toInt()),
                    formatClock(last),
                    when {
                        seconds > site.thresholds.staleAfterSeconds -> TelemetryCardState.ERROR
                        seconds > 2 -> TelemetryCardState.WARNING
                        else -> TelemetryCardState.NORMAL
                    }
                )
            )
        }
    }

    val reading = telemetry?.environment
    val service = reading?.let {
        listOf(
            CardSpec(
                stringResource(R.string.tel_temperature),
                stringResource(R.string.value_temperature, it.temperatureC),
                stringResource(R.string.value_temperature_detail, it.checkpointId, site.temperatureLimit(it.checkpointId) ?: 0.0),
                temperatureState(it, site, stale)
            ),
            CardSpec(
                stringResource(R.string.tel_humidity),
                stringResource(R.string.value_humidity, it.humidityPct),
                stringResource(R.string.value_humidity_detail, site.thresholds.humidityLow.warning, site.thresholds.humidityHigh.warning),
                humidityState(it, site.thresholds, stale)
            ),
            CardSpec(
                stringResource(R.string.tel_noise),
                stringResource(R.string.value_noise, it.noiseDba),
                stringResource(R.string.value_noise_detail, it.noiseBaselineDba),
                noiseState(it, site.thresholds, stale)
            )
        )
    }.orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionLabel(stringResource(R.string.telemetry_mandatory))
        CardGrid(mandatory)
        SectionLabel(
            reading?.let { stringResource(R.string.telemetry_service, checkpointLabel(site, it.checkpointId).orEmpty()) }
                ?: stringResource(R.string.telemetry_service_no_reading)
        )
        CardGrid(service)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CardGrid(cards: List<CardSpec>) {
    cards.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            pair.forEach { card ->
                TelemetryCard(card.label, card.value, card.detail, card.state, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
