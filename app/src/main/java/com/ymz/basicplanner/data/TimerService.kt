package com.ymz.basicplanner.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ymz.basicplanner.R
import com.ymz.basicplanner.ui.MainActivity

/**
 * Foreground service that keeps the rest timer running while the app is in the
 * background. Shows a live countdown notification and posts a completion
 * notification when the rest is over.
 */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.ymz.basicplanner.TIMER_START"
        const val ACTION_STOP = "com.ymz.basicplanner.TIMER_STOP"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_LABEL = "extra_label"

        private const val ONGOING_NOTIFICATION_ID = 4001
        private const val DONE_NOTIFICATION_ID = 4002
        private const val CHANNEL_ONGOING = "rest_timers"
        private const val CHANNEL_DONE = "rest_timers_done"

        private const val PREFS_NAME = "rest_timer_prefs"
        private const val PREFS_END_TIME = "end_time_millis"
        private const val PREFS_LABEL = "label"
        private const val PREFS_SECONDS = "seconds"

        fun start(context: Context, seconds: Int, label: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_LABEL, label)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Remaining seconds of the persisted timer, or null when none/expired. */
        fun persistedRemaining(context: Context): Int? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val endTime = prefs.getLong(PREFS_END_TIME, 0L)
            if (endTime <= 0L) return null
            val left = ((endTime - System.currentTimeMillis()) / 1000).toInt()
            return if (left > 0) left else null
        }

        fun persistedEndTime(context: Context): Long? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val end = prefs.getLong(PREFS_END_TIME, 0L)
            return if (end > 0L) end else null
        }

        fun persistedLabel(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREFS_LABEL, null)
        }

        private fun saveState(context: Context, seconds: Int, label: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(PREFS_END_TIME, System.currentTimeMillis() + seconds * 1000L)
                .putString(PREFS_LABEL, label)
                .putInt(PREFS_SECONDS, seconds)
                .apply()
        }

        private fun clearState(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var remainingSeconds = 0
    private var endTimeMillis = 0L
    private var label = ""

    private val ticker = object : Runnable {
        override fun run() {
            val left = ((endTimeMillis - System.currentTimeMillis()) / 1000).toInt()
            if (left > 0) {
                remainingSeconds = left
                updateOngoingNotification(left)
                handler.postDelayed(this, 1000L)
            } else {
                finishTimer()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handler.removeCallbacks(ticker)
                clearState(this)
                stopSelf()
                return START_STICKY
            }
            ACTION_START -> {
                label = intent.getStringExtra(EXTRA_LABEL) ?: ""
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0).coerceAtLeast(1)
                ensureChannels()
                saveState(this, seconds, label)
                startCountdown(seconds)
            }
            else -> {
                // Re-created by the system (START_STICKY) or started without an
                // explicit action: resume the persisted countdown if still running.
                val left = persistedRemaining(this)
                if (left != null && left > 0) {
                    label = persistedLabel(this) ?: ""
                    ensureChannels()
                    startCountdown(left)
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startCountdown(seconds: Int) {
        remainingSeconds = seconds
        endTimeMillis = System.currentTimeMillis() + seconds * 1000L
        handler.removeCallbacks(ticker)
        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(seconds))
        handler.postDelayed(ticker, 1000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun ensureChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.timer_channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                getString(R.string.timer_channel_done_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun buildOngoingNotification(secondsLeft: Int): Notification {
        val minutes = secondsLeft / 60
        val secs = secondsLeft % 60
        val timeText = if (minutes > 0) {
            getString(R.string.timer_time_mm_ss, minutes, secs)
        } else {
            getString(R.string.timer_time_ss, secs)
        }
        val contentText = if (label.isNotBlank()) {
            getString(R.string.timer_ongoing_label_text, label, timeText)
        } else {
            getString(R.string.timer_ongoing_text, timeText)
        }
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setContentTitle(getString(R.string.timer_ongoing_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent())
            .build()
    }

    private fun updateOngoingNotification(secondsLeft: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(secondsLeft))
    }

    private fun finishTimer() {
        clearState(this)
        val manager = getSystemService(NotificationManager::class.java)
        val contentText = if (label.isNotBlank()) {
            getString(R.string.timer_done_label_text, label)
        } else {
            getString(R.string.timer_done_text)
        }
        val done = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setContentTitle(getString(R.string.timer_done_title))
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()
        manager.notify(DONE_NOTIFICATION_ID, done)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
