package com.example.walllearn.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.SentenceSplitter.Sentence
import com.example.walllearn.databinding.ItemReaderPromptBinding
import com.example.walllearn.databinding.ItemReaderSentenceBinding
import com.example.walllearn.databinding.ItemReaderTitleBinding
import com.example.walllearn.model.Essay

/**
 * Rows: essay title, the issue prompt (if any), then one row per sentence. In
 * "listen" mode every sentence except the active one is dimmed, which together
 * with the word highlight gives the synced-lyrics look.
 */
class ReaderAdapter(
    context: Context,
    private val essay: Essay,
    private val sentences: List<Sentence>,
    private val onSentenceClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val hasPrompt = essay.prompt.isNotBlank()
    private val textColor = ContextCompat.getColor(context, R.color.reader_text)
    private val wordBg = ContextCompat.getColor(context, R.color.reader_word_bg)

    /** Adapter position of sentence 0. */
    val sentenceOffset = if (hasPrompt) 2 else 1

    var dimmed = false
        private set
    var activeSentence = -1
        private set
    private var activeWord: IntRange? = null
    private var flashSentence = -1

    fun positionOf(sentence: Int) = sentenceOffset + sentence

    fun setDimmed(on: Boolean) {
        if (dimmed == on) return
        dimmed = on
        notifyItemRangeChanged(sentenceOffset, sentences.size, PAYLOAD_ALPHA)
    }

    fun setActive(sentence: Int) {
        if (sentence == activeSentence && activeWord == null) return
        val old = activeSentence
        activeSentence = sentence
        activeWord = null
        if (old in sentences.indices) notifyItemChanged(positionOf(old), PAYLOAD_ACTIVE)
        if (sentence in sentences.indices) notifyItemChanged(positionOf(sentence), PAYLOAD_ACTIVE)
    }

    fun setActiveWord(sentence: Int, start: Int, end: Int) {
        if (sentence != activeSentence || sentence !in sentences.indices) return
        activeWord = start until end
        notifyItemChanged(positionOf(sentence), PAYLOAD_TEXT)
    }

    /** Briefly highlights a whole sentence (used when jumping to it from the notes sheet). */
    fun setFlash(sentence: Int) {
        val old = flashSentence
        flashSentence = sentence
        if (old in sentences.indices) notifyItemChanged(positionOf(old), PAYLOAD_TEXT)
        if (sentence in sentences.indices) notifyItemChanged(positionOf(sentence), PAYLOAD_TEXT)
    }

    override fun getItemCount() = sentenceOffset + sentences.size

    override fun getItemViewType(position: Int) = when {
        position == 0 -> TYPE_TITLE
        hasPrompt && position == 1 -> TYPE_PROMPT
        else -> TYPE_SENTENCE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TITLE -> TitleHolder(ItemReaderTitleBinding.inflate(inflater, parent, false))
            TYPE_PROMPT -> PromptHolder(ItemReaderPromptBinding.inflate(inflater, parent, false))
            else -> SentenceHolder(ItemReaderSentenceBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        when (holder) {
            is TitleHolder -> {
                val label = listOf(essay.theme, essay.instructionType)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                holder.binding.themeLabel.text = label
                holder.binding.themeLabel.visibility =
                    if (label.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
                holder.binding.titleText.text = essay.title
            }
            is PromptHolder -> holder.binding.promptText.text = essay.prompt
            is SentenceHolder -> bindSentence(holder, position - sentenceOffset, payloads)
        }
    }

    private fun bindSentence(holder: SentenceHolder, index: Int, payloads: List<Any>) {
        val view = holder.binding.sentenceText
        val targetAlpha = if (!dimmed || index == activeSentence) 1f else DIM_ALPHA
        val onlyAlpha = payloads.isNotEmpty() && payloads.all { it == PAYLOAD_ALPHA || it == PAYLOAD_ACTIVE }

        view.animate().cancel()
        if (payloads.isNotEmpty() && onlyAlpha) {
            view.animate().alpha(targetAlpha).setDuration(FADE_MS).start()
            if (payloads.contains(PAYLOAD_ACTIVE)) view.text = buildText(index) // clears/sets word highlight
            return
        }
        view.alpha = targetAlpha
        view.text = buildText(index)
        if (payloads.isEmpty()) {
            val startsParagraph = index == 0 || sentences[index].paragraph != sentences[index - 1].paragraph
            val topDp = if (startsParagraph && index > 0) 22 else 0
            view.updatePadding(top = (topDp * view.resources.displayMetrics.density).toInt())
            view.setOnClickListener { onSentenceClick(holder.bindingAdapterPosition - sentenceOffset) }
        }
    }

    private fun buildText(index: Int): CharSequence {
        val text = sentences[index].text
        val sb = SpannableStringBuilder(text)
        for (g in essay.glossary) {
            var from = 0
            while (true) {
                val at = text.indexOf(g.term, from, ignoreCase = true)
                if (at < 0) break
                sb.setSpan(UnderlineSpan(), at, at + g.term.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                from = at + g.term.length
            }
        }
        if (index == flashSentence) {
            sb.setSpan(
                BackgroundColorSpan(wordBg),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val word = activeWord
        if (index == activeSentence && word != null && word.first >= 0 && word.last < text.length) {
            sb.setSpan(
                BackgroundColorSpan(wordBg),
                word.first, word.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(ForegroundColorSpan(textColor), word.first, word.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    class TitleHolder(val binding: ItemReaderTitleBinding) : RecyclerView.ViewHolder(binding.root)
    class PromptHolder(val binding: ItemReaderPromptBinding) : RecyclerView.ViewHolder(binding.root)
    class SentenceHolder(val binding: ItemReaderSentenceBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_TITLE = 0
        const val TYPE_PROMPT = 1
        const val TYPE_SENTENCE = 2
        const val PAYLOAD_ALPHA = "alpha"
        const val PAYLOAD_ACTIVE = "active"
        const val PAYLOAD_TEXT = "text"
        const val DIM_ALPHA = 0.32f
        const val FADE_MS = 250L
    }
}
