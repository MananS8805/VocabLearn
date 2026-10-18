package com.example.walllearn.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.core.EssayRepository
import com.example.walllearn.core.EssaySpeaker
import com.example.walllearn.core.SentenceSplitter
import com.example.walllearn.core.WordRepository
import com.example.walllearn.databinding.ActivityEssayReaderBinding
import com.example.walllearn.databinding.ItemInsightPointBinding
import com.example.walllearn.databinding.ItemInsightSectionBinding
import com.example.walllearn.databinding.ItemInsightVocabBinding
import com.example.walllearn.databinding.SheetInsightsBinding
import com.example.walllearn.model.Essay
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Black-screen, white-text essay reader. "Read" is plain scrolling text; "Listen"
 * dims every sentence but the one being spoken, highlights the spoken word, and
 * keeps the active line centred, like synced lyrics.
 */
class EssayReaderActivity : AppCompatActivity(), EssaySpeaker.Listener {

    private lateinit var binding: ActivityEssayReaderBinding
    private lateinit var essay: Essay
    private lateinit var sentences: List<SentenceSplitter.Sentence>
    private lateinit var adapter: ReaderAdapter
    private lateinit var speaker: EssaySpeaker
    private val main = Handler(Looper.getMainLooper())

    private var listenMode = false
    private var ttsReady = false
    private var ttsAvailable = true
    /** The sentence the player is on (or will start from). */
    private var cursor = 0
    private var lastUserScrollAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = intent.getStringExtra(EXTRA_ESSAY_ID)?.let { EssayRepository.byId(this, it) }
        if (found == null) {
            finish()
            return
        }
        essay = found
        sentences = SentenceSplitter.splitParagraphs(essay.paragraphs)

        binding = ActivityEssayReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpEdgeToEdge(binding.content)

        cursor = AppPrefs.essayProgress(this, essay.id).coerceIn(0, (sentences.size - 1).coerceAtLeast(0))

        adapter = ReaderAdapter(this, essay, sentences) { onSentenceTapped(it) }
        binding.readerList.layoutManager = LinearLayoutManager(this)
        binding.readerList.adapter = adapter
        binding.readerList.itemAnimator = null // payload updates must not cross-fade rows
        binding.readerList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) lastUserScrollAt = System.currentTimeMillis()
            }
        })
        if (cursor > 0) {
            binding.readerList.post {
                (binding.readerList.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(adapter.positionOf(cursor), dp(96))
            }
        }

        speaker = EssaySpeaker(this, this)
        speaker.setSentences(sentences.map { it.text })

        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.insightsButton.setOnClickListener { showInsights() }
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) setListenMode(checkedId == R.id.modeListen)
        }
        binding.playButton.setOnClickListener { onPlayPressed() }
        binding.prevButton.setOnClickListener { goTo(cursor - 1) }
        binding.nextButton.setOnClickListener { goTo(cursor + 1) }
        binding.speedButton.setOnClickListener { cycleSpeed() }
        binding.pitchButton.setOnClickListener { cyclePitch() }
        binding.voiceButton.setOnClickListener { showVoicePicker() }
        updateSpeedLabel()
        updatePitchLabel()
        updateVoiceLabel()
        updateProgressLabel()
    }

    override fun onStop() {
        super.onStop()
        if (!::speaker.isInitialized) return
        speaker.pause()
        updatePlayState()
        AppPrefs.setEssayProgress(this, essay.id, cursor)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speaker.isInitialized) speaker.shutdown()
        main.removeCallbacksAndMessages(null)
    }

    // ---- Modes and playback -------------------------------------------------

    private fun setListenMode(on: Boolean) {
        if (listenMode == on) return
        listenMode = on
        binding.playerBar.visibility = if (on) View.VISIBLE else View.GONE
        adapter.setDimmed(on)
        if (on) {
            adapter.setActive(cursor)
            centerOn(cursor, force = true)
        } else {
            speaker.pause()
            updatePlayState()
        }
    }

    private fun onPlayPressed() {
        if (speaker.isPlaying) {
            speaker.pause()
            updatePlayState()
            return
        }
        if (!checkSpeechUsable()) return
        speaker.play(cursor)
        updatePlayState()
    }

    private fun onSentenceTapped(index: Int) {
        if (index !in sentences.indices) return
        if (!listenMode) binding.modeToggle.check(R.id.modeListen)
        cursor = index
        adapter.setActive(index)
        centerOn(index, force = true)
        updateProgressLabel()
        if (checkSpeechUsable()) {
            speaker.play(index)
            updatePlayState()
        }
    }

    private fun goTo(index: Int) {
        if (index !in sentences.indices) return
        cursor = index
        adapter.setActive(index)
        centerOn(index, force = true)
        updateProgressLabel()
        speaker.seek(index)
    }

    private fun checkSpeechUsable(): Boolean {
        if (!ttsAvailable) {
            Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_LONG).show()
            return false
        }
        if (!ttsReady) {
            Toast.makeText(this, R.string.tts_not_ready, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updatePlayState() {
        val playing = speaker.isPlaying
        binding.playButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        binding.playButton.contentDescription = getString(if (playing) R.string.cd_pause else R.string.cd_play)
        if (playing) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateProgressLabel() {
        binding.progressLabel.text =
            getString(R.string.sentence_progress_format, cursor + 1, sentences.size)
    }

    private fun cycleSpeed() {
        val current = AppPrefs.essaySpeechRate(this)
        val next = SPEEDS.firstOrNull { it > current + 0.01f } ?: SPEEDS.first()
        speaker.setRate(next)
        updateSpeedLabel()
    }

    private fun cyclePitch() {
        val current = AppPrefs.essayPitch(this)
        val next = PITCHES.firstOrNull { it < current - 0.01f } ?: PITCHES.first()
        speaker.setPitch(next)
        updatePitchLabel()
    }

    private fun updatePitchLabel() {
        val p = AppPrefs.essayPitch(this)
        binding.pitchButton.text = getString(R.string.pitch_format, "%.2f".format(p).trimEnd('0').trimEnd('.'))
    }

    private fun updateSpeedLabel() {
        val rate = AppPrefs.essaySpeechRate(this)
        val text = if (rate % 1f == 0f) "%.1f".format(rate) else "%.2f".format(rate).trimEnd('0')
        binding.speedButton.text = getString(R.string.speed_format, text)
    }

    private fun showVoicePicker() {
        if (!checkSpeechUsable()) return
        val voices = speaker.availableVoices()
        if (voices.isEmpty()) {
            Toast.makeText(this, R.string.voice_none, Toast.LENGTH_LONG).show()
            return
        }
        val current = voices.indexOf(speaker.voice)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.voice_title)
            .setSingleChoiceItems(
                voices.map { it.label }.toTypedArray(),
                current
            ) { dialog, which ->
                speaker.selectVoice(voices[which])
                updateVoiceLabel()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateVoiceLabel() {
        binding.voiceButton.text = speaker.voice.label
    }

    // ---- EssaySpeaker.Listener ---------------------------------------------

    override fun onReady(available: Boolean) {
        ttsReady = available
        ttsAvailable = available
        if (::binding.isInitialized) updateVoiceLabel()
    }

    override fun onSentence(index: Int) {
        cursor = index
        adapter.setActive(index)
        updateProgressLabel()
        centerOn(index, force = false)
    }

    override fun onWord(index: Int, start: Int, end: Int) = adapter.setActiveWord(index, start, end)

    override fun onFinished() {
        cursor = 0
        adapter.setActive(0)
        updatePlayState()
        updateProgressLabel()
        centerOn(0, force = true)
    }

    override fun onError() {
        updatePlayState()
        Toast.makeText(this, R.string.tts_error, Toast.LENGTH_SHORT).show()
    }

    // ---- Scrolling ----------------------------------------------------------

    /**
     * Smooth-scrolls so the sentence sits mid-screen. While the user is dragging
     * (or just did), automatic follow is skipped so it doesn't fight their finger.
     */
    private fun centerOn(sentence: Int, force: Boolean) {
        if (!force && System.currentTimeMillis() - lastUserScrollAt < USER_SCROLL_GRACE_MS) return
        val scroller = object : LinearSmoothScroller(this) {
            override fun calculateDtToFit(
                viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int
            ) = (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)

            override fun getVerticalSnapPreference() = SNAP_TO_ANY
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics) =
                SCROLL_MS_PER_INCH / displayMetrics.densityDpi
        }
        scroller.targetPosition = adapter.positionOf(sentence)
        binding.readerList.layoutManager?.startSmoothScroll(scroller)
    }

    // ---- Notes sheet --------------------------------------------------------

    private fun showInsights() {
        val dialog = BottomSheetDialog(this)
        val sheet = SheetInsightsBinding.inflate(layoutInflater)
        val inflater = LayoutInflater.from(this)
        val box = sheet.insightsContent

        fun section(title: Int) {
            val s = ItemInsightSectionBinding.inflate(inflater, box, false)
            s.root.setText(title)
            box.addView(s.root)
        }

        if (essay.points.isEmpty() && essay.glossary.isEmpty() && essay.structureNote.isBlank()) {
            val s = ItemInsightSectionBinding.inflate(inflater, box, false)
            s.root.setText(R.string.insights_none)
            box.addView(s.root)
        }

        if (essay.points.isNotEmpty()) {
            section(R.string.insights_points)
            for (p in essay.points) {
                val row = ItemInsightPointBinding.inflate(inflater, box, false)
                row.kindLabel.setText(kindLabel(p.kind))
                row.quoteText.text = p.quote
                row.whyText.text = p.why
                row.whyText.visibility = if (p.why.isBlank()) View.GONE else View.VISIBLE
                row.root.setOnClickListener {
                    dialog.dismiss()
                    jumpTo(SentenceSplitter.indexOfQuote(sentences, essay.paragraphs, p.quote))
                }
                box.addView(row.root)
            }
        }

        if (essay.glossary.isNotEmpty()) {
            section(R.string.insights_vocab)
            for (g in essay.glossary) {
                val row = ItemInsightVocabBinding.inflate(inflater, box, false)
                row.termText.text = g.term
                row.defText.text = g.definition
                val example = sentences.firstOrNull { it.text.contains(g.term, ignoreCase = true) }?.text.orEmpty()
                row.root.setOnClickListener {
                    val at = sentences.indexOfFirst { it.text.contains(g.term, ignoreCase = true) }
                    if (at >= 0) {
                        dialog.dismiss()
                        jumpTo(at)
                    }
                }
                row.addVocabButton.setOnClickListener {
                    val added = WordRepository.addWord(this, g.term, "", g.definition, example)
                    Toast.makeText(
                        this,
                        if (added) R.string.vocab_added else R.string.vocab_in_rotation,
                        Toast.LENGTH_SHORT
                    ).show()
                    row.addVocabButton.setIconResource(R.drawable.ic_check)
                }
                box.addView(row.root)
            }
        }

        if (essay.structureNote.isNotBlank()) {
            section(R.string.insights_structure)
            val note = ItemInsightPointBinding.inflate(inflater, box, false)
            note.kindLabel.visibility = View.GONE
            note.quoteText.text = essay.structureNote
            note.quoteText.textSize = 14f
            note.whyText.visibility = View.GONE
            note.root.isClickable = false
            note.root.background = null
            box.addView(note.root)
        }

        dialog.setContentView(sheet.root)
        dialog.show()
    }

    private fun kindLabel(kind: String): Int = when (kind) {
        "thesis" -> R.string.kind_thesis
        "complexity" -> R.string.kind_complexity
        "conclusion" -> R.string.kind_conclusion
        else -> R.string.kind_argument
    }

    /** Scrolls to a sentence and flashes it, leaving playback state alone. */
    private fun jumpTo(sentence: Int) {
        if (sentence !in sentences.indices) return
        cursor = sentence
        updateProgressLabel()
        if (listenMode) {
            adapter.setActive(sentence)
            speaker.seek(sentence)
        }
        centerOn(sentence, force = true)
        adapter.setFlash(sentence)
        main.postDelayed({ adapter.setFlash(-1) }, FLASH_MS)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ESSAY_ID = "essay_id"
        private val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f)
        /** Highest to lowest; tapping steps down, then wraps back to normal. */
        private val PITCHES = listOf(1.0f, 0.9f, 0.8f, 0.7f)
        private const val USER_SCROLL_GRACE_MS = 3000L
        private const val SCROLL_MS_PER_INCH = 120f
        private const val FLASH_MS = 1800L

        fun intent(context: Context, essayId: String) =
            Intent(context, EssayReaderActivity::class.java).putExtra(EXTRA_ESSAY_ID, essayId)
    }
}
