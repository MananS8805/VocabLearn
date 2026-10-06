package com.example.walllearn.core

import android.content.Context
import android.util.Log
import com.example.walllearn.model.GreWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

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
 * been shown, the list is reshuffled and the cycle starts again. Words
 * added mid-cycle are spliced into the upcoming part of the order rather
 * than restarting it.
 */
object WordRepository {

    private const val TAG = "WordRepository"
    private const val PREFS_NAME = "walllearn_prefs"
    private const val KEY_ORDER = "word_order"
    private const val KEY_POSITION = "word_position"
    private const val ASSET_PATH = "gre_words.json"
    private const val USER_WORDS_FILE = "user_words.json"

    /** New words are scheduled somewhere within this many upcoming wake-ups. */
    private const val NEW_WORD_WINDOW = 10

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
        // Splice the new word into the upcoming rotation now, rather than letting the
        // size mismatch trigger a full reshuffle that would throw away progress.
        val (order, position) = reconciledOrder(context)
        writeOrder(context, order, position)
        return true
    }

    /** True if [word] (case-insensitive) is already in the rotation. */
    fun contains(context: Context, word: String): Boolean {
        ensureWordsLoaded(context)
        val trimmed = word.trim()
        return words.any { it.word.trim().equals(trimmed, ignoreCase = true) }
    }

    /**
     * Removes a word the user added (bundled words can't be removed). Returns
     * true if it was found and removed. The rotation keeps its place.
     */
    @Synchronized
    fun removeWord(context: Context, word: String): Boolean {
        ensureWordsLoaded(context)
        val appContext = context.applicationContext
        val userWords = loadUserWords(appContext)
        val trimmed = word.trim()
        val target = userWords.firstOrNull { it.word.trim().equals(trimmed, ignoreCase = true) }
            ?: return false
        val index = words.indexOf(target)
        if (index < 0) return false

        val (order, position) = reconciledOrder(context)
        words = words.filterIndexed { i, _ -> i != index }
        persistUserWords(appContext, userWords - target)

        // Drop the removed index and shift everything after it down by one.
        val slot = order.indexOf(index)
        val newOrder = order.filter { it != index }.map { if (it > index) it - 1 else it }.toIntArray()
        var newPosition = if (slot in 0..position) position - 1 else position
        if (newOrder.isEmpty()) newPosition = -1
        writeOrder(context, newOrder, newPosition.coerceAtMost(newOrder.size - 1))
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
     * Returns the persisted order and position, repaired to cover the current
     * word list. Words appended since the order was saved (user-added or looked
     * up in the dictionary) are slotted in at random among the next
     * [NEW_WORD_WINDOW] upcoming words, so they show up soon without
     * disturbing what's already been seen. Anything unrecoverable falls back
     * to a fresh shuffle. Position is -1 when nothing has been shown yet.
     */
    private fun reconciledOrder(context: Context): Pair<IntArray, Int> {
        var order = readOrder(context)
        var position = prefs(context).getInt(KEY_POSITION, -1)

        val valid = order.size <= words.size &&
            order.all { it in words.indices } &&
            order.distinct().size == order.size
        if (!valid || order.isEmpty()) {
            return Pair(newShuffledOrder(words.size), -1)
        }
        position = position.coerceIn(-1, order.size - 1)

        if (order.size < words.size) {
            val list = order.toMutableList()
            for (newIndex in order.size until words.size) {
                val windowEnd = minOf(list.size, position + 1 + NEW_WORD_WINDOW)
                list.add(Random.nextInt(position + 1, windowEnd + 1), newIndex)
            }
            order = list.toIntArray()
        }
        return Pair(order, position)
    }

    /**
     * Returns the word currently "on screen" without advancing the
     * rotation. Returns null only if the word list failed to load or
     * no word has ever been shown yet.
     */
    fun currentWord(context: Context): GreWord? {
        ensureWordsLoaded(context)
        if (words.isEmpty()) return null
        val (order, position) = reconciledOrder(context)
        if (!order.contentEquals(readOrder(context))) writeOrder(context, order, position)
        return words[order[position.coerceAtLeast(0)]]
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

        var (order, position) = reconciledOrder(context)

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
        val position = prefs(context).getInt(KEY_POSITION, 0).coerceIn(0, maxOf(words.size - 1, 0))
        return Pair(position + 1, words.size)
    }

    fun totalWordCount(context: Context): Int {
        ensureWordsLoaded(context)
        return words.size
    }
}
