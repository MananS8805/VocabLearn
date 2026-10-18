package com.example.walllearn.core

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions

/**
 * Spanish to English (or, with [fromSpanish] false, English to Spanish) translation with
 * Google's on-device ML Kit models. Each direction's model (about 30 MB) is downloaded
 * once; after that every translation runs offline on the phone and the text never leaves
 * the device. Callbacks arrive on the main thread.
 */
class SpanishTranslator(val fromSpanish: Boolean = true) {

    private val client: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(if (fromSpanish) TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH)
            .setTargetLanguage(if (fromSpanish) TranslateLanguage.ENGLISH else TranslateLanguage.SPANISH)
            .build()
    )

    /** Makes sure the offline model is present, downloading it if needed. */
    fun prepare(onResult: (Boolean) -> Unit) {
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /** Translates one piece of text; the result is null if the model is missing or fails. */
    fun translate(text: String, onResult: (String?) -> Unit) {
        client.translate(text)
            .addOnSuccessListener { onResult(it.trim()) }
            .addOnFailureListener { onResult(null) }
    }

    /**
     * Translates [texts] one after another, reporting each result (null on failure) with
     * its index, then calls [onDone]. Stops early if [isCancelled] turns true.
     */
    fun translateAll(
        texts: List<String>,
        isCancelled: () -> Boolean,
        onEach: (index: Int, translation: String?) -> Unit,
        onDone: () -> Unit
    ) {
        fun next(i: Int) {
            if (i >= texts.size || isCancelled()) {
                onDone()
                return
            }
            translate(texts[i]) { result ->
                onEach(i, result)
                next(i + 1)
            }
        }
        next(0)
    }

    fun close() = client.close()
}
