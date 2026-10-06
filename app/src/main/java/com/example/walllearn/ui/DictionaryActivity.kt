package com.example.walllearn.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.Dictionary
import com.example.walllearn.core.WordRepository
import com.example.walllearn.databinding.ActivityDictionaryBinding
import com.example.walllearn.databinding.ItemSenseBinding
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Search-as-you-type over the offline [Dictionary]. Opening a word shows all
 * of its meanings and adds it (with its most common meaning) to the lock
 * screen rotation, so words you look up come back around for review.
 */
class DictionaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val adapter = ResultsAdapter { openWord(it) }

    /** Incremented on every keystroke so slow, stale searches are ignored. */
    private var searchGeneration = 0
    private var currentEntry: Dictionary.Entry? = null

    private val backFromDetail = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeDetail()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpEdgeToEdge(binding.content)
        onBackPressedDispatcher.addCallback(this, backFromDetail)

        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = adapter

        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.clearButton.setOnClickListener { binding.searchInput.text.clear() }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onQueryChanged(s?.toString().orEmpty())
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                adapter.words.firstOrNull()?.let { openWord(it) }
                true
            } else {
                false
            }
        }

        // Warm up the dictionary (first run copies it out of the APK) while the user types.
        executor.execute { Dictionary.search(applicationContext, "a", limit = 1) }

        binding.searchInput.requestFocus()
        showKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private fun onQueryChanged(query: String) {
        if (binding.detailScroll.visibility == View.VISIBLE) closeDetail(refocus = false)
        binding.clearButton.visibility = if (query.isEmpty()) View.INVISIBLE else View.VISIBLE

        val generation = ++searchGeneration
        mainHandler.removeCallbacksAndMessages(null)
        if (query.isBlank()) {
            showResults(emptyList(), query)
            return
        }
        // Small debounce so fast typing doesn't queue a query per keystroke.
        mainHandler.postDelayed({
            executor.execute {
                val results = Dictionary.search(applicationContext, query)
                mainHandler.post { if (generation == searchGeneration) showResults(results, query) }
            }
        }, 120)
    }

    private fun showResults(results: List<String>, query: String) {
        adapter.submit(results)
        binding.resultsList.scrollToPosition(0)
        binding.resultsList.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
        binding.messageText.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        binding.messageText.text = if (query.isBlank()) {
            getString(R.string.dictionary_intro)
        } else {
            getString(R.string.dictionary_no_results, query.trim())
        }
    }

    private fun openWord(word: String) {
        hideKeyboard()
        executor.execute {
            val entry = Dictionary.lookup(applicationContext, word) ?: return@execute
            val added = addToRotation(entry)
            mainHandler.post {
                showDetail(entry)
                if (added) {
                    Snackbar.make(binding.root, R.string.dictionary_added, Snackbar.LENGTH_LONG)
                        .setAction(R.string.btn_undo) { toggleRotation() }
                        .show()
                }
            }
        }
    }

    /** Adds [entry]'s most common meaning to the rotation; false if it was already there. */
    private fun addToRotation(entry: Dictionary.Entry): Boolean {
        val primary = entry.senses.first()
        return WordRepository.addWord(
            applicationContext, entry.word, primary.pos, primary.definition, primary.example
        )
    }

    private fun showDetail(entry: Dictionary.Entry) {
        currentEntry = entry
        binding.detailWord.text = entry.word.replaceFirstChar { it.uppercase() }
        updateRotationButton()

        binding.sensesContainer.removeAllViews()
        var previousPos: String? = null
        entry.senses.forEachIndexed { i, sense ->
            val row = ItemSenseBinding.inflate(layoutInflater, binding.sensesContainer, true)
            row.posHeading.text = sense.pos
            row.posHeading.visibility = if (sense.pos != previousPos) View.VISIBLE else View.GONE
            row.definitionText.text = getString(R.string.sense_format, i + 1, sense.definition)
            row.exampleText.text = getString(R.string.sense_example_format, sense.example)
            row.exampleText.visibility = if (sense.example.isBlank()) View.GONE else View.VISIBLE
            previousPos = sense.pos
        }

        binding.detailScroll.scrollTo(0, 0)
        binding.detailScroll.visibility = View.VISIBLE
        binding.resultsList.visibility = View.GONE
        binding.messageText.visibility = View.GONE
        backFromDetail.isEnabled = true
    }

    private fun updateRotationButton() {
        val entry = currentEntry ?: return
        val inRotation = WordRepository.contains(this, entry.word)
        binding.rotationButton.setText(
            if (inRotation) R.string.btn_in_rotation else R.string.btn_add_to_rotation
        )
        binding.rotationButton.setIconResource(if (inRotation) R.drawable.ic_check else R.drawable.ic_add)
        binding.rotationButton.setOnClickListener { toggleRotation() }
    }

    private fun toggleRotation() {
        val entry = currentEntry ?: return
        executor.execute {
            val message = when {
                !WordRepository.contains(applicationContext, entry.word) ->
                    if (addToRotation(entry)) R.string.dictionary_added else null
                WordRepository.removeWord(applicationContext, entry.word) -> R.string.dictionary_removed
                // Bundled GRE words can't be removed.
                else -> R.string.dictionary_builtin
            }
            mainHandler.post {
                updateRotationButton()
                message?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }
            }
        }
    }

    private fun closeDetail(refocus: Boolean = true) {
        currentEntry = null
        backFromDetail.isEnabled = false
        binding.detailScroll.visibility = View.GONE
        showResults(adapter.words, binding.searchInput.text.toString())
        if (refocus) {
            binding.searchInput.requestFocus()
            showKeyboard()
        }
    }

    private fun showKeyboard() {
        binding.searchInput.post {
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        binding.searchInput.clearFocus()
    }

    private class ResultsAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ResultsAdapter.Holder>() {

        var words: List<String> = emptyList()
            private set

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(newWords: List<String>) {
            words = newWords
            notifyDataSetChanged()
        }

        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_dictionary_result, parent, false) as TextView
            )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val word = words[position]
            holder.text.text = word
            holder.text.setOnClickListener { onClick(word) }
        }

        override fun getItemCount() = words.size
    }
}
