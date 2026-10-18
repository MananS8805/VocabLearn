package com.example.walllearn.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.SpanishTranslator
import com.example.walllearn.core.TextImporter
import com.example.walllearn.core.UserTextRepository
import com.example.walllearn.databinding.ActivityImportTextBinding
import com.example.walllearn.databinding.ItemImportRowBinding

/**
 * Paste a text, split it into card-sized lines, fix them up, optionally translate them
 * offline with ML Kit, and save it as a text in the lock screen rotation. Also receives
 * plain text shared from other apps.
 */
class ImportTextActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportTextBinding
    private val rows = ArrayList<Row>()
    private val adapter = RowAdapter()
    private var translator: SpanishTranslator? = null
    private var translating = false
    private var destroyed = false

    private val backHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showInputStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val light = applyAppLook()
        super.onCreate(savedInstanceState)
        binding = ActivityImportTextBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpEdgeToEdge(binding.content, light)
        onBackPressedDispatcher.addCallback(this, backHandler)

        binding.rowList.layoutManager = LinearLayoutManager(this)
        binding.rowList.adapter = adapter
        binding.rowList.itemAnimator = null

        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.splitButton.setOnClickListener { onSplit() }
        binding.translateButton.setOnClickListener { translateAll() }
        binding.saveButton.setOnClickListener { save() }

        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            binding.bodyInput.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            if (!subject.isNullOrBlank()) binding.titleInput.setText(subject)
        }
    }

    override fun onDestroy() {
        destroyed = true
        translator?.close()
        translator = null
        super.onDestroy()
    }

    private fun selectedMode() = when (binding.splitModeToggle.checkedButtonId) {
        R.id.modeLines -> TextImporter.Mode.LINES
        R.id.modeSentences -> TextImporter.Mode.SENTENCES
        else -> TextImporter.Mode.AUTO
    }

    private fun onSplit() {
        val raw = binding.bodyInput.text?.toString().orEmpty()
        if (raw.isBlank()) {
            Toast.makeText(this, R.string.import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val pieces = TextImporter.split(raw, selectedMode())
        if (pieces.isEmpty()) {
            Toast.makeText(this, R.string.import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        rows.clear()
        rows.addAll(pieces.map { Row(it, "") })
        adapter.notifyDataSetChanged()
        showReviewStep()
    }

    private fun showReviewStep() {
        binding.inputGroup.visibility = View.GONE
        binding.reviewGroup.visibility = View.VISIBLE
        binding.screenTitle.setText(R.string.import_review_title)
        backHandler.isEnabled = true
        updateSummary()
    }

    private fun showInputStep() {
        if (translating) return
        binding.reviewGroup.visibility = View.GONE
        binding.inputGroup.visibility = View.VISIBLE
        binding.screenTitle.setText(R.string.import_title)
        backHandler.isEnabled = false
    }

    private fun updateSummary() {
        binding.reviewSummary.text =
            getString(R.string.import_cards_format, rows.size) + "  ·  " + getString(R.string.import_review_hint)
    }

    private fun translateAll() {
        if (translating) return
        val pending = rows.indices.filter { rows[it].en.isBlank() && rows[it].es.isNotBlank() }
        if (pending.isEmpty()) return
        val engine = translator ?: SpanishTranslator().also { translator = it }
        setTranslating(true, getString(R.string.import_downloading))
        engine.prepare { ready ->
            if (destroyed) return@prepare
            if (!ready) {
                setTranslating(false)
                Toast.makeText(this, R.string.import_model_failed, Toast.LENGTH_LONG).show()
                return@prepare
            }
            var failed = 0
            engine.translateAll(
                texts = pending.map { rows[it].es },
                isCancelled = { destroyed },
                onEach = { i, result ->
                    if (destroyed) return@translateAll
                    val row = rows.getOrNull(pending[i])
                    if (row != null && result != null) {
                        row.en = result
                        adapter.notifyItemChanged(pending[i], PAYLOAD_TRANSLATION)
                    } else {
                        failed++
                    }
                    binding.translateStatus.text = getString(R.string.import_translating, i + 1, pending.size)
                },
                onDone = {
                    if (destroyed) return@translateAll
                    setTranslating(false)
                    if (failed > 0) Toast.makeText(this, R.string.import_translate_failed, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun setTranslating(active: Boolean, status: String = "") {
        translating = active
        binding.translateProgress.visibility = if (active) View.VISIBLE else View.GONE
        binding.translateStatus.visibility = if (active) View.VISIBLE else View.GONE
        binding.translateStatus.text = status
        binding.translateButton.isEnabled = !active
        binding.saveButton.isEnabled = !active
    }

    private fun save() {
        val lines = rows
            .filter { it.es.isNotBlank() }
            .map { UserTextRepository.Line(it.es.trim(), it.en.trim()) }
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.import_none, Toast.LENGTH_SHORT).show()
            return
        }
        val saved = UserTextRepository.add(
            this, binding.titleInput.text?.toString().orEmpty(), lines, binding.orderedSwitch.isChecked
        )
        if (saved == null) {
            Toast.makeText(this, R.string.import_full, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.import_saved, Toast.LENGTH_LONG).show()
        finish()
    }

    private class Row(var es: String, var en: String)

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.Holder>() {

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemImportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

        override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(PAYLOAD_TRANSLATION)) holder.bindTranslation(rows[position]) else onBindViewHolder(holder, position)
        }

        inner class Holder(private val b: ItemImportRowBinding) : RecyclerView.ViewHolder(b.root) {
            private var esWatcher: TextWatcher? = null
            private var enWatcher: TextWatcher? = null

            fun bind(row: Row) {
                esWatcher?.let { b.esInput.removeTextChangedListener(it) }
                enWatcher?.let { b.enInput.removeTextChangedListener(it) }
                b.esInput.setText(row.es)
                b.enInput.setText(row.en)
                esWatcher = watcher { rows.getOrNull(bindingAdapterPosition)?.es = it }
                enWatcher = watcher { rows.getOrNull(bindingAdapterPosition)?.en = it }
                b.esInput.addTextChangedListener(esWatcher)
                b.enInput.addTextChangedListener(enWatcher)

                b.deleteButton.setOnClickListener {
                    val at = bindingAdapterPosition
                    if (at >= 0) {
                        rows.removeAt(at)
                        notifyItemRemoved(at)
                        updateSummary()
                    }
                }
                b.mergeButton.setOnClickListener {
                    val at = bindingAdapterPosition
                    if (at >= 0 && at + 1 < rows.size) {
                        val a = rows[at]
                        val next = rows.removeAt(at + 1)
                        a.es = "${a.es.trim()} ${next.es.trim()}".trim()
                        a.en = "${a.en.trim()} ${next.en.trim()}".trim()
                        notifyItemRemoved(at + 1)
                        notifyItemChanged(at)
                        updateSummary()
                    }
                }
                b.mergeButton.visibility = View.VISIBLE
            }

            fun bindTranslation(row: Row) {
                if (b.enInput.text?.toString() != row.en) b.enInput.setText(row.en)
            }

            private fun watcher(onChange: (String) -> Unit) = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
            }
        }
    }

    private companion object {
        const val PAYLOAD_TRANSLATION = "translation"
    }
}
