package com.example.walllearn.ui

import android.content.Context
import android.provider.Settings

object Motion {
    /** False when the user has turned system animations off (Settings > Accessibility or Developer options). */
    fun enabled(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}
