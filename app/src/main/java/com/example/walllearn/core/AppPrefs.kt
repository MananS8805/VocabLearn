package com.example.walllearn.core

import android.content.Context

/** Small shared-preferences flags that don't belong to [WordRepository]. */
object AppPrefs {
    private const val PREFS_NAME = "walllearn_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    fun isServiceEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .apply()
    }
}
