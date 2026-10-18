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

    // --- Language mix -----------------------------------------------------------------
    // All new keys: an upgraded install reads them as defaults (English only), so the
    // rotation behaves exactly as before until the user moves a slider.

    /** Relative frequency (0..[MAX_WEIGHT]) of each source in the lock screen rotation. */
    fun mixWeight(context: Context, source: String): Int =
        prefs(context).getInt(KEY_MIX_PREFIX + source, if (source == SOURCE_EN) 1 else 0)
            .coerceIn(0, MAX_WEIGHT)

    fun setMixWeight(context: Context, source: String, weight: Int) {
        prefs(context).edit().putInt(KEY_MIX_PREFIX + source, weight.coerceIn(0, MAX_WEIGHT)).apply()
    }

    fun mixCredits(context: Context): IntArray {
        val raw = prefs(context).getString(KEY_MIX_CREDITS, null) ?: return IntArray(SOURCES.size)
        val parts = raw.split(",").mapNotNull { it.toIntOrNull() }
        return if (parts.size == SOURCES.size) parts.toIntArray() else IntArray(SOURCES.size)
    }

    fun setMixCredits(context: Context, credits: IntArray) {
        prefs(context).edit().putString(KEY_MIX_CREDITS, credits.joinToString(",")).apply()
    }

    /** Which source produced the card currently on the lock screen. */
    fun currentSource(context: Context): String =
        prefs(context).getString(KEY_CURRENT_SOURCE, SOURCE_EN) ?: SOURCE_EN

    fun setCurrentSource(context: Context, source: String) {
        prefs(context).edit().putString(KEY_CURRENT_SOURCE, source).apply()
    }

    /** True (default) when Spanish moves up a level by itself after one pass through the current one. */
    fun spanishAuto(context: Context): Boolean = prefs(context).getBoolean(KEY_ES_AUTO, true)

    fun setSpanishAuto(context: Context, auto: Boolean) {
        prefs(context).edit().putBoolean(KEY_ES_AUTO, auto).apply()
    }

    fun spanishLevel(context: Context): Int = prefs(context).getInt(KEY_ES_LEVEL, 1)

    fun setSpanishLevel(context: Context, level: Int) {
        prefs(context).edit().putInt(KEY_ES_LEVEL, level).apply()
    }

    fun spanishOrder(context: Context, level: Int): String? =
        prefs(context).getString(KEY_ES_ORDER_PREFIX + level, null)

    fun spanishPosition(context: Context, level: Int): Int =
        prefs(context).getInt(KEY_ES_POS_PREFIX + level, -1)

    fun setSpanishOrder(context: Context, level: Int, order: String?, position: Int) {
        prefs(context).edit()
            .putString(KEY_ES_ORDER_PREFIX + level, order)
            .putInt(KEY_ES_POS_PREFIX + level, position)
            .apply()
    }

    /** Index of the imported text whose lines are currently being shown. */
    fun activeTextId(context: Context): String? = prefs(context).getString(KEY_ACTIVE_TEXT, null)

    fun setActiveTextId(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_ACTIVE_TEXT, id).apply()
    }

    // --- Spanish voice ("Hear it" on Spanish cards) ------------------------------------

    /** Which Spanish accent to prefer: [ACCENT_AUTO] (best voice installed), US/Latin America, Mexico or Spain. */
    fun spanishAccent(context: Context): String = prefs(context).getString(KEY_ES_ACCENT, ACCENT_AUTO) ?: ACCENT_AUTO

    fun setSpanishAccent(context: Context, accent: String) {
        prefs(context).edit().putString(KEY_ES_ACCENT, accent).apply()
    }

    /** Lower than 1 is deeper; the default sits low because device voices run high and thin. */
    fun spanishPitch(context: Context): Float = prefs(context).getFloat(KEY_ES_PITCH, 0.8f)

    fun setSpanishPitch(context: Context, pitch: Float) {
        prefs(context).edit().putFloat(KEY_ES_PITCH, pitch).apply()
    }

    fun spanishRate(context: Context): Float = prefs(context).getFloat(KEY_ES_RATE, 0.85f)

    fun setSpanishRate(context: Context, rate: Float) {
        prefs(context).edit().putFloat(KEY_ES_RATE, rate).apply()
    }

    const val ACCENT_AUTO = "auto"
    const val ACCENT_US = "us"
    const val ACCENT_MX = "mx"
    const val ACCENT_ES = "es"
    private const val KEY_ES_ACCENT = "es_voice_accent"
    private const val KEY_ES_PITCH = "es_voice_pitch"
    private const val KEY_ES_RATE = "es_voice_rate"

    const val SOURCE_EN = "en"
    const val SOURCE_ES = "es"
    const val SOURCE_TX = "tx"
    val SOURCES = listOf(SOURCE_EN, SOURCE_ES, SOURCE_TX)
    const val MAX_WEIGHT = 10

    private const val KEY_MIX_PREFIX = "mix_weight_"
    private const val KEY_MIX_CREDITS = "mix_credits"
    private const val KEY_CURRENT_SOURCE = "current_source"
    private const val KEY_ES_AUTO = "es_auto"
    private const val KEY_ES_LEVEL = "es_level"
    private const val KEY_ES_ORDER_PREFIX = "es_order_"
    private const val KEY_ES_POS_PREFIX = "es_pos_"
    private const val KEY_ACTIVE_TEXT = "active_text"

    /** Index of the sentence the user last reached in an essay (0 if never opened). */
    fun essayProgress(context: Context, essayId: String): Int =
        prefs(context).getInt(KEY_PROGRESS_PREFIX + essayId, 0)

    fun setEssayProgress(context: Context, essayId: String, sentence: Int) {
        prefs(context).edit().putInt(KEY_PROGRESS_PREFIX + essayId, sentence).apply()
    }
}
