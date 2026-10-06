package com.example.walllearn.core

import android.content.Context
import android.content.SharedPreferences
import com.example.walllearn.model.LockTheme
import com.example.walllearn.model.LockThemes

/** Small shared-preferences flags that don't belong to [WordRepository]. */
object AppPrefs {
    private const val PREFS_NAME = "walllearn_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_THEME = "lock_theme"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_SPEECH_RATE = "essay_speech_rate"
    private const val KEY_VOICE = "essay_voice"
    private const val KEY_PITCH = "essay_pitch"
    private const val KEY_PROGRESS_PREFIX = "essay_progress_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isServiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    const val APP_THEME_DARK = "dark"
    const val APP_THEME_LIGHT = "light"
    const val APP_THEME_BAUHAUS = "bauhaus"

    /**
     * Look of the app itself (not the lock screen): [APP_THEME_DARK] (default), [APP_THEME_LIGHT]
     * or [APP_THEME_BAUHAUS]. The retired "dusk" look is read as dark.
     */
    fun appTheme(context: Context): String =
        when (val stored = prefs(context).getString(KEY_APP_THEME, APP_THEME_DARK)) {
            APP_THEME_LIGHT, APP_THEME_BAUHAUS -> stored
            else -> APP_THEME_DARK
        }

    fun setAppTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_APP_THEME, theme).apply()
    }

    fun lockTheme(context: Context): LockTheme =
        LockThemes.byId(prefs(context).getString(KEY_THEME, LockTheme.DEFAULT_ID))

    fun setLockTheme(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME, id).apply()
    }

    fun essaySpeechRate(context: Context): Float = prefs(context).getFloat(KEY_SPEECH_RATE, 1.0f)

    fun setEssaySpeechRate(context: Context, rate: Float) {
        prefs(context).edit().putFloat(KEY_SPEECH_RATE, rate).apply()
    }

    /** Voice pitch multiplier; below 1 is deeper. Defaults lower because the device voices run high. */
    fun essayPitch(context: Context): Float = prefs(context).getFloat(KEY_PITCH, 0.9f)

    fun setEssayPitch(context: Context, pitch: Float) {
        prefs(context).edit().putFloat(KEY_PITCH, pitch).apply()
    }

    /** Id of the voice the user picked (see EssayVoices), or null for the default. */
    fun essayVoice(context: Context): String? = prefs(context).getString(KEY_VOICE, null)

    fun setEssayVoice(context: Context, voiceName: String?) {
        prefs(context).edit().putString(KEY_VOICE, voiceName).apply()
    }

    /** Index of the sentence the user last reached in an essay (0 if never opened). */
    fun essayProgress(context: Context, essayId: String): Int =
        prefs(context).getInt(KEY_PROGRESS_PREFIX + essayId, 0)

    fun setEssayProgress(context: Context, essayId: String, sentence: Int) {
        prefs(context).edit().putInt(KEY_PROGRESS_PREFIX + essayId, sentence).apply()
    }
}
