package com.example.walllearn.core

import android.content.Context
import com.example.walllearn.model.GreWord

/**
 * Decides what the lock screen shows next across the three sources - the English word
 * list, the Spanish course and the user's own texts - in the ratio the user set with the
 * sliders. Each source keeps its own no-repeat sequence; this only chooses *which source*
 * speaks next.
 *
 * With the default weights (English 1, others 0) this is exactly the old behaviour:
 * every call goes straight to [WordRepository].
 */
object Rotation {

    /** Weights per source, in [AppPrefs.SOURCES] order, with empty sources forced to zero. */
    fun effectiveWeights(context: Context): IntArray {
        val available = booleanArrayOf(
            true,
            SpanishRepository.isAvailable(context),
            UserTextRepository.hasContent(context)
        )
        return IntArray(AppPrefs.SOURCES.size) { i ->
            if (available[i]) AppPrefs.mixWeight(context, AppPrefs.SOURCES[i]) else 0
        }
    }

    @Synchronized
    fun advance(context: Context): GreWord {
        val weights = effectiveWeights(context)
        val credits = AppPrefs.mixCredits(context)
        val picked = WeightedPicker.pick(weights, credits)
        AppPrefs.setMixCredits(context, credits)

        val order = if (picked < 0) listOf(AppPrefs.SOURCE_EN) else listOf(AppPrefs.SOURCES[picked])
        for (source in order) {
            val word = advanceFrom(context, source)
            if (word != null) {
                AppPrefs.setCurrentSource(context, source)
                return word
            }
        }
        // The chosen source came up empty (e.g. its asset failed to load): English always works.
        AppPrefs.setCurrentSource(context, AppPrefs.SOURCE_EN)
        return WordRepository.advance(context)
    }

    /** What is on screen now, without moving the rotation. */
    fun current(context: Context): GreWord? {
        val fromSource = when (AppPrefs.currentSource(context)) {
            AppPrefs.SOURCE_ES -> SpanishRepository.current(context)
            AppPrefs.SOURCE_TX -> UserTextRepository.current(context)
            else -> null
        }
        return fromSource ?: WordRepository.currentWord(context)
    }

    private fun advanceFrom(context: Context, source: String): GreWord? = when (source) {
        AppPrefs.SOURCE_ES -> SpanishRepository.advance(context)
        AppPrefs.SOURCE_TX -> UserTextRepository.advance(context)
        else -> WordRepository.advance(context)
    }
}
