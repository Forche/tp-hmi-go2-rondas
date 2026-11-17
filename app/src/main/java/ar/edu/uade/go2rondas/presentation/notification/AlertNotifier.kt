package ar.edu.uade.go2rondas.presentation.notification

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.edu.uade.go2rondas.MainActivity
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.coroutines.ApplicationScope
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.usecase.NewAlert
import ar.edu.uade.go2rondas.domain.usecase.ObserveNewAlertsUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveSiteConfigUseCase
import ar.edu.uade.go2rondas.presentation.common.components.alertDetailText
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.titleRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avisa con una notificación cada alerta nueva mientras la app está en segundo plano: el operador
 * puede usar otras herramientas del NOC y enterarse igual. Con la app a la vista no notifica, porque
 * el Panel ya muestra la alerta. Tocar la notificación abre el detalle de la alerta.
 */
@Singleton
class AlertNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val observeNewAlerts: ObserveNewAlertsUseCase,
    private val observeSiteConfig: ObserveSiteConfigUseCase
) {
    private var startedActivities = 0

    fun start(application: Application) {
        createChannels()
        application.registerActivityLifecycleCallbacks(VisibilityTracker())
        scope.launch {
            observeNewAlerts().collect { if (startedActivities == 0) notify(it) }
        }
    }

    private suspend fun notify(item: NewAlert) {
        if (!canNotify()) return
        val site = observeSiteConfig().first()
        val checkpoint = item.alert.checkpointId?.let { site.checkpoint(it) }
            ?.let { context.getString(R.string.checkpoint_label, it.id, it.name) }
        show(item, checkpoint)
    }

    private fun show(item: NewAlert, checkpoint: String?) {
        val alert = item.alert
        val title = context.getString(
            R.string.notification_alert_title,
            context.getString(alert.severity.labelRes()),
            context.getString(alert.code.titleRes()),
            item.robot.name
        )
        val detail = alertDetailText(context, alert, checkpoint)
        val text = if (alert.requiresDecision) context.getString(R.string.notification_alert_decision, detail) else detail
        val key = "${alert.id}@${alert.timestampMillis}".hashCode()
        val open = PendingIntent.getActivity(
            context,
            key,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ALERT_ID, alert.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val critical = alert.severity == AlertSeverity.CRITICAL
        val notification = NotificationCompat.Builder(context, if (critical) CHANNEL_CRITICAL else CHANNEL_WARNING)
            .setSmallIcon(if (critical) R.drawable.ic_error_circle else R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key, notification)
        } catch (e: SecurityException) {
            // El operador quitó el permiso entre el chequeo y el aviso: la alerta igual queda en la app.
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CRITICAL, context.getString(R.string.channel_critical), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WARNING, context.getString(R.string.channel_warning), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Cuenta las pantallas visibles: con cero, la app está en segundo plano. */
    private inner class VisibilityTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val CHANNEL_CRITICAL = "alertas_criticas"
        const val CHANNEL_WARNING = "alertas"
    }
}
