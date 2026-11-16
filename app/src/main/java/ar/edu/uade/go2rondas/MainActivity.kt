package ar.edu.uade.go2rondas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.edu.uade.go2rondas.core.theme.Go2RondasTheme
import ar.edu.uade.go2rondas.presentation.Go2RondasApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Go2RondasTheme {
                Go2RondasApp()
            }
        }
    }
}
