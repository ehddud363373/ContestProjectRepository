package com.contestproject.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.contestproject.app.MainActivity
import com.contestproject.app.R
import com.contestproject.app.ShimpyoApp
import com.contestproject.app.domain.NapState
import java.text.DateFormat
import java.util.Date

/**
 * 쪽잠 동안만 살아 있는 포그라운드 서비스. 일은 NapController가 하고,
 * 여기서는 프로세스를 지키고 알림(쉬는 중 / 깨우는 중)만 갱신한다.
 */
class NapService : LifecycleService() {

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var lastKey: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startAsForeground(buildNotification(NapState.Idle))

        val controller = (application as ShimpyoApp).napController
        lifecycleScope.launch {
            controller.state.collect { state ->
                if (state !is NapState.Active) {
                    ServiceCompat.stopForeground(this@NapService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                val key = notificationKey(state)
                if (key != lastKey) {
                    lastKey = key
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    private fun startAsForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun notificationKey(state: NapState): String = when (state) {
        is NapState.Resting -> "rest-${state.session.endAtMs}"
        is NapState.PreWake -> "prewake-${state.session.endAtMs}"
        is NapState.Waking -> "wake"
        is NapState.Alert -> "alert-${state.reason}"
        is NapState.DepartCheck -> "depart"
        NapState.Idle -> "idle"
    }

    private fun buildNotification(state: NapState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val urgent = state is NapState.Waking || state is NapState.Alert
        val text = when (state) {
            is NapState.Resting, is NapState.PreWake ->
                getString(R.string.notif_resting, formatTime(state.session.endAtMs))
            is NapState.Waking -> getString(R.string.notif_waking)
            is NapState.Alert -> getString(R.string.notif_alert)
            is NapState.DepartCheck -> getString(R.string.notif_depart)
            NapState.Idle -> getString(R.string.notif_preparing)
        }
        return NotificationCompat.Builder(this, if (urgent) CHANNEL_WAKE else CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(!urgent)
            .setSilent(!urgent)
            .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .apply { if (urgent) setFullScreenIntent(openApp, true) }
            .build()
    }

    private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    private fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_WAKE, getString(R.string.channel_wake), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // 소리는 앱이 직접 낸다(알림음 + 음성)
            }
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 1501
        private const val CHANNEL_STATUS = "nap_status"
        private const val CHANNEL_WAKE = "nap_wake"

        fun intent(context: Context) = Intent(context, NapService::class.java)
    }
}
