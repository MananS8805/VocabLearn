package com.example.walllearn.core

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.walllearn.model.EssayVoice
import com.example.walllearn.model.EssayVoices
import java.util.Locale

/**
 * Reads an essay aloud, one sentence per utterance, using the phone's own text-to-speech
 * engine, and reports which sentence (and, on API 26+, which word) is being spoken so the
 * UI can highlight it like synced lyrics. All [Listener] callbacks arrive on the main thread.
 *
 * Android's TextToSpeech has no pause: [pause] stops, and [play] restarts from the start of
 * the sentence that was interrupted. Every [play] begins a new "session"; callbacks from an
 * older session (e.g. the onDone/onStop that stop() triggers) are ignored so they can't be
 * mistaken for progress.
 */
class EssaySpeaker(context: Context, private val listener: Listener) {

    interface Listener {
        /** TTS finished initialising; [available] is false if no usable English voice exists. */
        fun onReady(available: Boolean)
        fun onSentence(index: Int)
        /** [start, end) is the spoken word's range within sentence [index]'s text. */
        fun onWord(index: Int, start: Int, end: Int)
        fun onFinished()
        fun onError()
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var sentences: List<String> = emptyList()
    private var session = 0

    var voice: EssayVoice = EssayVoices.byId(AppPrefs.essayVoice(appContext))
        private set

    /** Sentence most recently started; where [play] resumes after a [pause]. */
    var currentIndex = 0
        private set

    var isPlaying = false
        private set

    init {
        tts = TextToSpeech(appContext) { status ->
            main.post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    listener.onReady(false)
                    return@post
                }
                val lang = engine.setLanguage(Locale.US)
                ready = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED
                if (ready) {
                    engine.setOnUtteranceProgressListener(progress)
                    applyVoice(engine, voice)
                }
                listener.onReady(ready)
            }
        }
    }

    fun setSentences(texts: List<String>) {
        stopSpeaking()
        sentences = texts
        currentIndex = currentIndex.coerceIn(0, (texts.size - 1).coerceAtLeast(0))
    }

    /** The catalog voices that resolve to an installed device voice. */
    fun availableVoices(): List<EssayVoice> {
        val engine = tts ?: return emptyList()
        if (!ready) return emptyList()
        return EssayVoices.all.filter { resolve(engine, it) != null }
    }

    /** Switches voice and speaks a short sample so the choice can be judged by ear. */
    fun selectVoice(newVoice: EssayVoice) {
        val engine = tts ?: return
        val wasPlaying = isPlaying
        stopSpeaking()
        voice = newVoice
        AppPrefs.setEssayVoice(appContext, newVoice.id)
        applyVoice(engine, newVoice)
        if (wasPlaying) {
            play(currentIndex)
        } else {
            engine.setSpeechRate(AppPrefs.essaySpeechRate(appContext))
            engine.speak(EssayVoices.SAMPLE_TEXT, TextToSpeech.QUEUE_FLUSH, Bundle(), "sample")
        }
    }

    /** Starts (or resumes) reading at [from], defaulting to where the last session stopped. */
    fun play(from: Int = currentIndex) {
        val engine = tts ?: return
        if (!ready || sentences.isEmpty()) return
        val start = if (from in sentences.indices) from else 0
        val mine = ++session
        engine.stop()
        applyVoice(engine, voice)
        engine.setSpeechRate(AppPrefs.essaySpeechRate(appContext))
        isPlaying = true
        currentIndex = start
        for (i in start until sentences.size) {
            val result = engine.speak(sentences[i], TextToSpeech.QUEUE_ADD, Bundle(), "$mine:$i")
            if (result != TextToSpeech.SUCCESS) {
                isPlaying = false
                listener.onError()
                return
            }
        }
    }

    fun pause() {
        if (!isPlaying) return
        stopSpeaking()
    }

    /** Jumps to [index]: if playing, reading continues from there; otherwise just moves the cursor. */
    fun seek(index: Int) {
        if (index !in sentences.indices) return
        if (isPlaying) play(index) else currentIndex = index
    }

    fun setRate(rate: Float) {
        AppPrefs.setEssaySpeechRate(appContext, rate)
        tts?.setSpeechRate(rate)
        // The rate applies to utterances queued after this call, so restart the current one.
        if (isPlaying) play(currentIndex)
    }

    fun setPitch(pitch: Float) {
        AppPrefs.setEssayPitch(appContext, pitch)
        val engine = tts ?: return
        engine.setPitch(pitch)
        // Pitch applies to utterances queued after this call; restart or play a sample so it can be heard.
        if (isPlaying) {
            play(currentIndex)
        } else if (ready) {
            stopSpeaking()
            engine.setSpeechRate(AppPrefs.essaySpeechRate(appContext))
            engine.speak(EssayVoices.SAMPLE_TEXT, TextToSpeech.QUEUE_FLUSH, Bundle(), "sample")
        }
    }

    fun shutdown() {
        session++
        isPlaying = false
        main.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun stopSpeaking() {
        session++ // invalidates every queued utterance's callbacks
        isPlaying = false
        tts?.stop()
    }

    private fun resolve(engine: TextToSpeech, v: EssayVoice): Voice? {
        val voices = try {
            engine.voices.orEmpty()
        } catch (e: Exception) {
            return null
        }
        val region = voices.filter {
            it.locale.language == "en" && it.locale.country.equals(v.country, ignoreCase = true) &&
                !it.features.orEmpty().contains("notInstalled") && !it.isNetworkConnectionRequired
        }
        return v.names.firstNotNullOfOrNull { n -> region.firstOrNull { it.name == n } }
            ?: region.sortedBy { it.name }.firstOrNull { it.name.contains("-x-") }
    }

    private fun applyVoice(engine: TextToSpeech, v: EssayVoice) {
        engine.setPitch(AppPrefs.essayPitch(appContext))
        val chosen = resolve(engine, v)
        if (chosen != null) {
            engine.voice = chosen
            Log.i(TAG, "Using voice ${chosen.name} for ${v.id}")
        }
    }

    private fun parse(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.split(":") ?: return null
        if (parts.size != 2) return null
        val s = parts[0].toIntOrNull() ?: return null
        val i = parts[1].toIntOrNull() ?: return null
        return s to i
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (s, i) = parse(utteranceId) ?: return
            main.post {
                if (s != session) return@post
                currentIndex = i
                listener.onSentence(i)
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (s, i) = parse(utteranceId) ?: return
            main.post { if (s == session) listener.onWord(i, start, end) }
        }

        override fun onDone(utteranceId: String?) {
            val (s, i) = parse(utteranceId) ?: return
            main.post {
                if (s != session) return@post
                if (i == sentences.size - 1) {
                    isPlaying = false
                    currentIndex = 0
                    listener.onFinished()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (s, _) = parse(utteranceId) ?: return
            main.post {
                if (s != session) return@post
                isPlaying = false
                listener.onError()
            }
        }
    }

    private companion object {
        const val TAG = "EssaySpeaker"
    }
}
