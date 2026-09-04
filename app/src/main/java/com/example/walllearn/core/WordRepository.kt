package com.example.walllearn.core

import android.content.Context
import android.util.Log
import com.example.walllearn.model.GreWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Loads the bundled GRE word list plus any user-added words and keeps
 * track of a shuffled, no-repeat rotation through the combined set.
 *
 * The bundled list ships read-only inside the APK (assets/gre_words.json),
 * so words added from the app are kept separately in a writable JSON file
 * in internal storage ([USER_WORDS_FILE]) and merged with the bundled list
 * every time it's loaded - together they form the "master" word list.
 *
 * The rotation order and current position are persisted in
 * SharedPreferences so the sequence survives process death, device
 * reboots, and app updates. Once every word in the shuffled order has
 * been shown, the list is reshuffled and the cycle starts again.
 */
object WordRepository {

    private const val TAG = "WordRepository"
    private const val PREFS_NAME = "walllearn_prefs"
    private const val KEY_ORDER = "word_order"
    private const val KEY_POSITION = "word_position"
    private const val ASSET_PATH = "gre_words.json"
    private const val USER_WORDS_FILE = "user_words.json"

    @Volatile
    private var words: List<GreWord> = emptyList()

    /** Loads the bundled + user word lists exactly once per process. */
    @Synchronized
    private fun ensureWordsLoaded(context: Context) {
        if (words.isNotEmpty()) return
        val appContext = context.applicationContext
        val bundled = parseWordsJson(
            appContext.assets.open(ASSET_PATH).use { it.readBytes().toString(Charsets.UTF_8) }
        )
        val user = loadUserWords(appContext)
        words = bundled + user
        Log.i(TAG, "Loaded ${bundled.size} bundled + ${user.size} user words")
    }

    private fun parseWordsJson(jsonText: String): List<GreWord> {
        val array = JSONArray(jsonText)
        val loaded = ArrayList<GreWord>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            loaded.add(
                GreWord(
                    word = obj.getString("word"),
                    pos = obj.optString("pos", ""),
                    meaning = obj.getString("meaning"),
                    example = obj.optString("example", "")
                )
            )
        }
        return loaded
    }

    private fun loadUserWords(context: Context): List<GreWord> {
        val file = File(context.filesDir, USER_WORDS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            parseWordsJson(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user words from $USER_WORDS_FILE", e)
            emptyList()
        }
    }

    private fun persistUserWords(context: Context, userWords: List<GreWord>) {
        val array = JSONArray()
        for (w in userWords) {
            array.put(
                JSONObject()
                    .put("word", w.word)
                    .put("pos", w.pos)
                    .put("meaning", w.meaning)
                    .put("example", w.example)
            )
        }
        File(context.filesDir, USER_WORDS_FILE).writeText(array.toString(), Charsets.UTF_8)
    }

    /**
     * Adds a user-supplied word to the master list if it isn't already
     * present (case-insensitive match on the word itself). Persists it to
     * internal storage so it survives restarts and joins the rotation
     * alongside the bundled list. Returns true if the word was added,
     * false if a matching word already existed and nothing changed.
     */
    @Synchronized
    fun addWord(context: Context, word: String, pos: String, meaning: String, example: String): Boolean {
        ensureWordsLoaded(context)
        val trimmedWord = word.trim()
        if (words.any { it.word.trim().equals(trimmedWord, ignoreCase = true) }) {
            return false
        }

        val newWord = GreWord(word = trimmedWord, pos = pos.trim(), meaning = meaning.trim(), example = example.trim())
        words = words + newWord

        val appContext = context.applicationContext
        persistUserWords(appContext, loadUserWords(appContext) + newWord)
        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readOrder(context: Context): IntArray {
        val raw = prefs(context).getString(KEY_ORDER, null)
        if (raw.isNullOrEmpty()) return IntArray(0)
        return try {
            raw.split(",").map { it.toInt() }.toIntArray()
        } catch (e: NumberFormatException) {
            IntArray(0)
        }
    }

    private fun writeOrder(context: Context, order: IntArray, position: Int) {
        prefs(context).edit()
            .putString(KEY_ORDER, order.joinToString(","))
            .putInt(KEY_POSITION, position)
            .apply()
    }

    private fun newShuffledOrder(size: Int): IntArray =
        (0 until size).toMutableList().apply { shuffle() }.toIntArray()

    /**
     * Returns the word currently "on screen" without advancing the
     * rotation. Returns null only if the word list failed to load or
     * no word has ever been shown yet.
     */
    fun currentWord(context: Context): GreWord? {
        ensureWordsLoaded(context)
        if (words.isEmpty()) return null
        var order = readOrder(context)
        if (order.size != words.size) {
            order = newShuffledOrder(words.size)
            writeOrder(context, order, 0)
        }
        val position = prefs(context).getInt(KEY_POSITION, 0).coerceIn(0, order.size - 1)
        return words[order[position]]
    }

    /**
     * Advances to the next word in the shuffled, no-repeat order,
     * persists the new position, and returns it. Reshuffles
     * automatically once the whole list has been shown.
     */
    @Synchronized
    fun advance(context: Context): GreWord {
        ensureWordsLoaded(context)
        require(words.isNotEmpty()) { "GRE word list is empty; check assets/gre_words.json" }

        var order = readOrder(context)
        var position = prefs(context).getInt(KEY_POSITION, -1)

        if (order.size != words.size) {
            order = newShuffledOrder(words.size)
            position = -1
        }

        position += 1
        if (position >= order.size) {
            order = newShuffledOrder(words.size)
            position = 0
        }

        writeOrder(context, order, position)
        return words[order[position]]
    }

    /** 1-based position of the current word and the total list size, for display. */
    fun progress(context: Context): Pair<Int, Int> {
        ensureWordsLoaded(context)
        val order = readOrder(context)
        val size = if (order.size == words.size) order.size else words.size
        val position = prefs(context).getInt(KEY_POSITION, 0).coerceIn(0, maxOf(size - 1, 0))
        return Pair(position + 1, words.size)
    }

    fun totalWordCount(context: Context): Int {
        ensureWordsLoaded(context)
        return words.size
    }
}
