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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.Dictionary
import com.example.walllearn.core.WebDictionary
import com.example.walllearn.core.WebWordStore
import com.example.walllearn.core.WordRepository
import com.example.walllearn.databinding.FragmentWordsBinding
import com.example.walllearn.databinding.ItemSenseBinding
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Search-as-you-type over the offline [Dictionary]. Opening a word shows all
 * of its meanings and adds it (with its most common meaning) to the lock
 * screen rotation, so words you look up come back around for review.
 */
class WordsFragment : Fragment() {

    private var _binding: FragmentWordsBinding? = null
    private val binding get() = _binding!!
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val adapter = ResultsAdapter { openWord(it) }

    /** Incremented on every keystroke so slow, stale searches are ignored. */
    private var searchGeneration = 0
    private var currentEntry: Dictionary.Entry? = null
    private var currentFromWeb = false

    private val backFromDetail = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeDetail()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backFromDetail)

        binding.resultsList.layoutManager = LinearLayoutManager(requireContext())
        binding.resultsList.adapter = adapter

        binding.clearButton.setOnClickListener { binding.searchInput.text.clear() }
        binding.webLookupButton.setOnClickListener { lookUpOnline(binding.searchInput.text.toString().trim()) }
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

        // Warm up the dictionary (first run copies it out of the APK) in the background.
        executor.execute { Dictionary.search(appCtx, "a", limit = 1) }
    }

    /** While this tab is hidden its back-from-detail handler must not swallow Back on other tabs. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) hideKeyboard()
        backFromDetail.isEnabled = !hidden && _binding?.detailScroll?.visibility == View.VISIBLE
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
    private val appCtx get() = requireContext().applicationContext

    /** Runs [block] on the main thread unless the view is already gone. */
    private fun post(block: () -> Unit) {
        mainHandler.post { if (_binding != null) block() }
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
            if (_binding == null) return@postDelayed
            executor.execute {
                val offline = Dictionary.search(appCtx, query)
                val results = (offline + WebWordStore.search(appCtx, query)).distinct()
                post { if (generation == searchGeneration) showResults(results, query) }
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
        // Offer the web only when the exact word isn't already here.
        val q = query.trim()
        val offerWeb = q.isNotEmpty() && WebDictionary.isLookupable(q) &&
            results.none { it.equals(q, ignoreCase = true) }
        binding.webLookupButton.visibility = if (offerWeb) View.VISIBLE else View.GONE
        binding.webLookupButton.isEnabled = true
        if (offerWeb) binding.webLookupButton.text = getString(R.string.web_lookup_button, q)
    }

    private fun lookUpOnline(word: String) {
        if (!WebDictionary.isLookupable(word)) return
        hideKeyboard()
        binding.webLookupButton.isEnabled = false
        binding.resultsList.visibility = View.GONE
        binding.messageText.visibility = View.VISIBLE
        binding.messageText.text = getString(R.string.web_lookup_loading, word)
        executor.execute {
            val outcome = WebDictionary.lookup(word)
            var added = false
            if (outcome is WebDictionary.Result.Found) {
                WebWordStore.save(appCtx, outcome.entry)
                added = addToRotation(outcome.entry)
            }
            post {
                when (outcome) {
                    is WebDictionary.Result.Found -> {
                        showDetail(outcome.entry, fromWeb = true)
                        if (added) {
                            Snackbar.make(binding.root, R.string.dictionary_added, Snackbar.LENGTH_LONG)
                                .setAction(R.string.btn_undo) { toggleRotation() }
                                .show()
                        }
                    }
                    WebDictionary.Result.NotFound -> {
                        binding.webLookupButton.isEnabled = true
                        binding.messageText.text = getString(R.string.web_lookup_not_found, word)
                    }
                    WebDictionary.Result.Unavailable -> {
                        binding.webLookupButton.isEnabled = true
                        binding.messageText.text = getString(R.string.web_lookup_unavailable)
                    }
                }
            }
        }
    }

    private fun openWord(word: String) {
        hideKeyboard()
        executor.execute {
            val offline = Dictionary.lookup(appCtx, word)
            val entry = offline ?: WebWordStore.get(appCtx, word) ?: return@execute
            val added = addToRotation(entry)
            post {
                showDetail(entry, fromWeb = offline == null)
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
            appCtx, entry.word, primary.pos, primary.definition, primary.example
        )
    }

    private fun showDetail(entry: Dictionary.Entry, fromWeb: Boolean = false) {
        currentEntry = entry
        currentFromWeb = fromWeb
        binding.creditText.setText(if (fromWeb) R.string.web_credit else R.string.dictionary_credit)
        binding.webLookupButton.visibility = View.GONE
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
        val inRotation = WordRepository.contains(requireContext(), entry.word)
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
                !WordRepository.contains(appCtx, entry.word) ->
                    if (addToRotation(entry)) R.string.dictionary_added else null
                WordRepository.removeWord(appCtx, entry.word) -> R.string.dictionary_removed
                // Bundled GRE words can't be removed.
                else -> R.string.dictionary_builtin
            }
            post {
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
            requireContext().getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
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
