package com.example.walllearn.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.PhraseSpeaker
import com.example.walllearn.core.SpanishTranslator
import com.example.walllearn.core.UserTextRepository
import com.example.walllearn.databinding.ActivityTextViewerBinding
import com.example.walllearn.databinding.ItemViewerLineBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows one imported text line by line. Tapping any word translates it on the device,
 * reads it aloud, or saves it to the "Saved words" text so it joins the lock screen rotation.
 */
class TextViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextViewerBinding
    private lateinit var textId: String
    private var translator: SpanishTranslator? = null
    private var speaker: PhraseSpeaker? = null
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val light = applyAppLook()
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_TEXT_ID)
        val text = id?.let { UserTextRepository.get(this, it) }
        if (id == null || text == null) {
            Toast.makeText(this, R.string.viewer_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        textId = id
        binding = ActivityTextViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpEdgeToEdge(binding.content, light)

        binding.viewerTitle.text = text.title.ifBlank { getString(R.string.mix_texts) }
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val adapter = LineAdapter(text.lines) { word -> onWordTapped(word) }
        binding.lineList.layoutManager = LinearLayoutManager(this)
        binding.lineList.adapter = adapter

        binding.orderedSwitch.isChecked = text.ordered
        binding.orderedSwitch.setOnCheckedChangeListener { _, checked ->
            UserTextRepository.setOrdered(this, textId, checked)
        }
    }

    override fun onDestroy() {
        destroyed = true
        translator?.close()
        speaker?.shutdown()
        super.onDestroy()
    }

    private fun onWordTapped(word: String) {
        val engine = translator ?: SpanishTranslator().also { translator = it }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(word)
            .setMessage(R.string.word_sheet_translating)
            .setPositiveButton(R.string.word_save, null)
            .setNeutralButton(R.string.cd_speak) { _, _ -> speak(word) }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
        dialog.show()

        var translation: String? = null
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            setOnClickListener {
                val added = UserTextRepository.saveWord(this@TextViewerActivity, word, translation.orEmpty())
                Toast.makeText(
                    this@TextViewerActivity,
                    if (added) R.string.word_saved else R.string.word_already_saved,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
        // The neutral button speaks without closing the dialog.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { speak(word) }

        engine.prepare { ready ->
            if (destroyed || !dialog.isShowing) return@prepare
            if (!ready) {
                dialog.setMessage(getString(R.string.word_sheet_unavailable))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                return@prepare
            }
            engine.translate(word) { result ->
                if (destroyed || !dialog.isShowing) return@translate
                translation = result
                dialog.setMessage(result ?: getString(R.string.word_sheet_unavailable))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
        }
    }

    private fun speak(word: String) {
        val engine = speaker ?: PhraseSpeaker(this) {
            if (!destroyed) Toast.makeText(this, R.string.tts_spanish_unavailable, Toast.LENGTH_LONG).show()
        }.also { speaker = it }
        engine.speak(word, "es-ES")
    }

    private class LineAdapter(
        private val lines: List<UserTextRepository.Line>,
        private val onWord: (String) -> Unit
    ) : RecyclerView.Adapter<LineAdapter.Holder>() {

        override fun getItemCount() = lines.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemViewerLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val line = lines[position]
            holder.b.lineText.text = tappable(line.text)
            holder.b.lineText.movementMethod = LinkMovementMethod.getInstance()
            holder.b.lineText.highlightColor = 0
            holder.b.lineTranslation.text = line.translation
            holder.b.lineTranslation.visibility = if (line.translation.isBlank()) View.GONE else View.VISIBLE
        }

        /** Makes every word in [text] a tap target without changing how the text looks. */
        private fun tappable(text: String): CharSequence {
            val spannable = SpannableString(text)
            for (match in WORD.findAll(text)) {
                val word = match.value
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onWord(word.trim('\'', '’', '-'))
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                    }
                }, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return spannable
        }

        class Holder(val b: ItemViewerLineBinding) : RecyclerView.ViewHolder(b.root)
    }

    companion object {
        private const val EXTRA_TEXT_ID = "text_id"
        private val WORD = Regex("[\\p{L}\\p{M}][\\p{L}\\p{M}'’-]*")

        fun intent(context: Context, textId: String): Intent =
            Intent(context, TextViewerActivity::class.java).putExtra(EXTRA_TEXT_ID, textId)
    }
}
