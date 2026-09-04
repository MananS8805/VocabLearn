package com.example.walllearn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.walllearn.R
import com.example.walllearn.core.WallpaperController
import com.example.walllearn.ui.MainActivity

/**
 * An always-on foreground service that listens for the screen turning
 * on and immediately regenerates + applies the wallpaper so a fresh
 * word is showing by the time the lock screen appears.
 *
 * Android requires a visible notification for any long-running
 * background listener like this one; the notification is kept as
 * unobtrusive as possible.
 */
class WallpaperUpdateService : Service() {

    private var screenOnReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerScreenOnReceiver()
        // Make sure something is already showing the moment the service starts.
        WallpaperController.reapplyCurrentWallpaper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky so the system tries to bring the listener back if it's
        // killed under memory pressure.
        return START_STICKY
    }

    override fun onDestroy() {
        screenOnReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Already unregistered; nothing to do.
            }
        }
        screenOnReceiver = null
        super.onDestroy()
    }

    private fun registerScreenOnReceiver() {
        if (screenOnReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    Log.i(TAG, "Screen turned on - refreshing wallpaper")
                    WallpaperController.applyNextWallpaper(applicationContext)
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        screenOnReceiver = receiver
    }

    private fun startInForeground() {
        createNotificationChannelIfNeeded()

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WallpaperUpdateService"
        private const val CHANNEL_ID = "walllearn_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, WallpaperUpdateService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WallpaperUpdateService::class.java))
        }
    }
}
