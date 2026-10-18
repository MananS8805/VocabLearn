package com.example.walllearn.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads a single word or sentence aloud in the language it is written in (Spanish for
 * the language cards). The engine starts on first use and is released with [shutdown].
 * [onUnavailable] fires when the device has no voice for the language.
 */
class PhraseSpeaker(context: Context, private val onUnavailable: () -> Unit) {

    private var ready = false
    private var failed = false
    private var pending: Pair<String, String>? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pending?.let { (text, tag) -> pending = null; speak(text, tag) }
        } else {
            failed = true
            onUnavailable()
        }
    }

    fun speak(text: String, languageTag: String) {
        if (failed) return onUnavailable()
        if (!ready) {
            pending = text to languageTag
            return
        }
        val result = tts.setLanguage(Locale.forLanguageTag(languageTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onUnavailable()
            return
        }
        tts.setSpeechRate(0.9f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "phrase")
    }

    fun shutdown() {
        pending = null
        tts.stop()
        tts.shutdown()
    }
}
