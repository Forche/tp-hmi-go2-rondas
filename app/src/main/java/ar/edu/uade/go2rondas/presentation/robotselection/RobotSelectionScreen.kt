package ar.edu.uade.go2rondas.presentation.robotselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.theme.Go2RondasTheme
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.model.RobotSource

@Composable
fun RobotSelectionScreen(
    onOpenPanel: () -> Unit,
    viewModel: RobotSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RobotSelectionUiEffect.OpenPanel -> onOpenPanel()
            }
        }
    }
    RobotSelectionContent(state = state, onRobotSelected = viewModel::onRobotSelected)
}

@Composable
fun RobotSelectionContent(
    state: RobotSelectionUiState,
    onRobotSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.robots_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.robots_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.robots_available),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.robots, key = { it.id }) { robot ->
                RobotCard(
                    robot = robot,
                    connecting = state.connectingRobotId == robot.id,
                    onClick = { onRobotSelected(robot.id) }
                )
            }
            item {
                Text(
                    stringResource(R.string.robots_mujoco_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RobotCard(robot: Robot, connecting: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.robot_avatar), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(robot.name, style = MaterialTheme.typography.titleMedium)
            val detail = when (robot.source) {
                RobotSource.LOCAL_SIMULATOR -> stringResource(R.string.robot_source_local)
                RobotSource.MUJOCO_SERVER -> stringResource(R.string.robot_source_mujoco, robot.address.orEmpty())
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (connecting) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RobotSelectionPreview() {
    Go2RondasTheme {
        RobotSelectionContent(
            state = RobotSelectionUiState(
                robots = listOf(
                    Robot("go2-01", "Go2-01", RobotSource.LOCAL_SIMULATOR),
                    Robot("go2-02", "Go2-02", RobotSource.MUJOCO_SERVER, "192.168.0.20:8765")
                )
            ),
            onRobotSelected = {}
        )
    }
}
