package ar.edu.uade.go2rondas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.edu.uade.go2rondas.core.theme.Go2RondasTheme
import ar.edu.uade.go2rondas.presentation.Go2RondasApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Alerta a abrir porque el operador tocó su notificación; la app la consume y la vuelve a null. */
    private val alertToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readAlert(intent)
        setContent {
            Go2RondasTheme {
                Go2RondasApp(alertToOpen = alertToOpen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readAlert(intent)
    }

    private fun readAlert(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ALERT_ID)?.let { alertToOpen.value = it }
    }

    companion object {
        const val EXTRA_ALERT_ID = "alert_id"
    }
}
