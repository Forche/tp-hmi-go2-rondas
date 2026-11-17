package ar.edu.uade.go2rondas

import android.app.Application
import ar.edu.uade.go2rondas.presentation.notification.AlertNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class Go2RondasApplication : Application() {

    @Inject
    lateinit var alertNotifier: AlertNotifier

    override fun onCreate() {
        super.onCreate()
        alertNotifier.start(this)
    }
}
