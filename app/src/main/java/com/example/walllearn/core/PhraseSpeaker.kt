package com.example.walllearn.core

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Reads a single word or sentence aloud in the language it is written in (Spanish for
 * the language cards). The engine starts on first use and is released with [shutdown].
 * [onUnavailable] fires (on the main thread) when the device has no usable voice for the
 * language or the engine fails to speak.
 *
 * The voice is chosen, not left to the engine's default: it prefers the accent the learner
 * picked, then installed voices that work offline, then the highest quality one, and applies
 * the learner's pitch and speed. The engine default is high and fast, which sounds thin and
 * breathless. If the chosen voice fails to speak, it falls back to the engine's default voice.
 */
class PhraseSpeaker(context: Context, private val onUnavailable: () -> Unit) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private var pending: Pair<String, String>? = null
    private var usedCustomVoice = false
    private var lastRequest: Pair<String, String>? = null
    private var retriedWithDefault = false

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.setOnUtteranceProgressListener(progressListener)
            // Ordinary media speech: audible on the speaker and under the media volume.
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            pending?.let { (text, tag) -> pending = null; speak(text, tag) }
        } else {
            Log.e(TAG, "Text-to-speech engine failed to start (status $status)")
            failed = true
            main.post { onUnavailable() }
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = Unit

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = handleError(-1)

        override fun onError(utteranceId: String?, errorCode: Int) = handleError(errorCode)
    }

    /** One retry with the engine's own default voice when a picked voice produces an error. */
    private fun handleError(code: Int) {
        Log.e(TAG, "Speech failed (error $code), customVoice=$usedCustomVoice")
        val request = lastRequest
        if (usedCustomVoice && !retriedWithDefault && request != null) {
            retriedWithDefault = true
            main.post { speakInternal(request.first, request.second, useCustomVoice = false) }
        } else {
            main.post { onUnavailable() }
        }
    }

    fun speak(text: String, languageTag: String) {
        if (failed) return onUnavailable()
        if (!ready) {
            pending = text to languageTag
            return
        }
        retriedWithDefault = false
        speakInternal(text, languageTag, useCustomVoice = true)
    }

    private fun speakInternal(text: String, languageTag: String, useCustomVoice: Boolean) {
        lastRequest = text to languageTag
        if (!selectLanguage(languageTag)) {
            Log.w(TAG, "No Spanish voice data for $languageTag on engine ${tts.defaultEngine}")
            onUnavailable()
            return
        }
        usedCustomVoice = false
        if (useCustomVoice) {
            val voice = pickVoice()
            if (voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                usedCustomVoice = true
                Log.i(TAG, "Voice ${voice.name} (${voice.locale}, quality ${voice.quality}, network ${voice.isNetworkConnectionRequired}) on ${tts.defaultEngine}")
            } else {
                Log.w(TAG, "Could not use a picked voice (${voice?.name}); using the engine default")
            }
        }
        tts.setPitch(AppPrefs.spanishPitch(appContext))
        tts.setSpeechRate(AppPrefs.spanishRate(appContext))
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "phrase")
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "speak() returned ERROR")
            handleError(-1)
        }
    }

    private fun selectLanguage(tag: String): Boolean {
        fun ok(r: Int) = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        // Try the exact variant first, then the bare language (any Spanish voice will do).
        return ok(tts.setLanguage(Locale.forLanguageTag(tag))) ||
            ok(tts.setLanguage(Locale(tag.substringBefore('-'))))
    }

    /** The best installed Spanish voice for the chosen accent, or null to keep the engine's default. */
    private fun pickVoice(): Voice? {
        val spanish = try {
            tts.voices.orEmpty().filter {
                it.locale.language == "es" &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features &&
                    // "es-US-language" style entries are placeholders that resolve to a default, not real voices.
                    !it.name.endsWith("-language", ignoreCase = true)
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (spanish.isEmpty()) return null
        val accent = AppPrefs.spanishAccent(appContext)
        fun matches(v: Voice): Boolean = when (accent) {
            AppPrefs.ACCENT_US -> v.locale.country == "US" || v.locale.country == "419"
            AppPrefs.ACCENT_MX -> v.locale.country == "MX"
            AppPrefs.ACCENT_ES -> v.locale.country == "ES"
            else -> true
        }
        val pool = spanish.filter(::matches).ifEmpty { spanish }
        return pool.maxWithOrNull(
            compareBy<Voice>({ !it.isNetworkConnectionRequired }, { it.quality }, { -it.latency })
        )
    }

    fun shutdown() {
        pending = null
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "PhraseSpeaker"
    }
}
