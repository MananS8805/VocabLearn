package com.example.walllearn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.service.WallpaperUpdateService

/**
 * Restarts the wallpaper-updating service after a reboot, but only if
 * the user had WallLearn turned on before the device restarted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AppPrefs.isServiceEnabled(context)) {
            WallpaperUpdateService.start(context)
        }
    }
}
