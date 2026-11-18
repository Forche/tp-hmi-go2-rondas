package ar.edu.uade.go2rondas.presentation.camera

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.core.theme.statusColors
import ar.edu.uade.go2rondas.domain.model.LiveCameraState
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.PhotoPlaceholder
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.rememberNow

/** Más de esto sin imagen nueva y se avisa que la imagen no está al día (como con la telemetría). */
private const val STALE_MILLIS = 2_000L

@Composable
fun LiveCameraScreen(onBack: () -> Unit, viewModel: LiveCameraViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = rememberNow(500)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.camera_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now,
            onBack = onBack
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val current = state) {
                UiState.Idle, UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(current.message)
                is UiState.Success -> when (val camera = current.data.camera) {
                    LiveCameraState.NoCamera -> PhotoPlaceholder(stringResource(R.string.camera_none))
                    LiveCameraState.Waiting -> PhotoPlaceholder(stringResource(R.string.camera_waiting))
                    is LiveCameraState.Frame -> LiveFrame(camera, now)
                }
            }
            Text(
                stringResource(R.string.camera_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveFrame(frame: LiveCameraState.Frame, now: Long) {
    val bitmap = remember(frame) { BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)?.asImageBitmap() }
    if (bitmap == null) {
        PhotoPlaceholder(stringResource(R.string.camera_waiting))
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.camera_title),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
    )
    val ageSeconds = ((now - frame.timestampMillis).coerceAtLeast(0) / 1_000).toInt()
    val status = MaterialTheme.statusColors
    if (now - frame.timestampMillis > STALE_MILLIS) {
        Text(stringResource(R.string.camera_stale, ageSeconds), style = MaterialTheme.typography.labelLarge, color = status.stale)
    } else {
        Text(stringResource(R.string.camera_live), style = MaterialTheme.typography.labelLarge, color = status.ok)
    }
}
