package ar.edu.uade.go2rondas.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.RobotSource

/**
 * Barra superior de todas las pantallas: título, robot activo, conexión y batería.
 * Debajo, si no llegan datos, el banner de desconexión.
 */
@Composable
fun RobotTopBar(
    title: String,
    snapshot: RobotSnapshot?,
    now: Long,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val link = snapshot?.connection?.state ?: LinkState.DISCONNECTED
    val robot = snapshot?.robot
    Column(modifier) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = if (onBack == null) 16.dp else 4.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (robot != null) {
                        val source = when (robot.source) {
                            RobotSource.LOCAL_SIMULATOR -> R.string.robot_subtitle_local
                            RobotSource.MUJOCO_SERVER -> R.string.robot_subtitle_mujoco
                        }
                        Text(
                            stringResource(R.string.robot_subtitle, robot.name, stringResource(source)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ConnectionIndicator(link)
                    BatteryIndicator(
                        percent = snapshot?.telemetry?.battery?.percent,
                        stale = link == LinkState.DISCONNECTED
                    )
                }
                actions()
            }
        }
        if (snapshot != null && snapshot.robot != null && link == LinkState.DISCONNECTED) {
            DisconnectionBanner(lastUpdateMillis = snapshot.connection.lastUpdateMillis, now = now)
        }
    }
}

@Composable
fun DisconnectionBanner(lastUpdateMillis: Long?, now: Long, modifier: Modifier = Modifier) {
    val onError = MaterialTheme.colorScheme.onError
    Surface(color = MaterialTheme.colorScheme.error, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_wifi_off), contentDescription = null, tint = onError)
            Column {
                Text(stringResource(R.string.banner_disconnected_title), style = MaterialTheme.typography.labelLarge, color = onError)
                val detail = if (lastUpdateMillis == null) {
                    stringResource(R.string.banner_waiting_first_data)
                } else {
                    stringResource(R.string.banner_disconnected_detail, ((now - lastUpdateMillis) / 1_000).coerceAtLeast(0).toInt())
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = onError)
            }
        }
    }
}
