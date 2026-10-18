package com.example.walllearn.core

import android.content.Context
import android.util.Log
import com.example.walllearn.model.GreWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Texts the user pasted in (a paragraph, a poem, anything), already split into
 * card-sized lines with optional translations. Kept in their own file in internal
 * storage ([FILE]) so they never touch the word list, and never leave the device.
 *
 * Each text remembers its own shuffled or in-order sequence and position. The rotation
 * stays on one text until it has been shown through, then moves to the next enabled one,
 * so a pasted story or poem reads in order instead of being interleaved line by line.
 */
object UserTextRepository {

    private const val TAG = "UserTextRepository"
    private const val FILE = "user_texts.json"
    const val SAVED_WORDS_ID = "saved_words"
    private const val MAX_TEXTS = 40

    data class Line(val text: String, val translation: String)

    data class UserText(
        val id: String,
        val title: String,
        val ordered: Boolean,
        val enabled: Boolean,
        val lines: List<Line>,
        val order: IntArray,
        val position: Int
    ) {
        val isSavedWords get() = id == SAVED_WORDS_ID
    }

    private var cache: MutableList<UserText>? = null

    @Synchronized
    private fun load(context: Context): MutableList<UserText> {
        cache?.let { return it }
        val file = File(context.applicationContext.filesDir, FILE)
        val list = ArrayList<UserText>()
        if (file.exists()) {
            try {
                val array = JSONArray(file.readText(Charsets.UTF_8))
                for (i in 0 until array.length()) list.add(parseText(array.getJSONObject(i)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read $FILE", e)
            }
        }
        cache = list
        return list
    }

    private fun parseText(o: JSONObject): UserText {
        val linesJson = o.getJSONArray("lines")
        val lines = (0 until linesJson.length()).map {
            val l = linesJson.getJSONObject(it)
            Line(l.getString("t"), l.optString("tr", ""))
        }
        val orderJson = o.optJSONArray("order")
        val order = IntArray(orderJson?.length() ?: 0) { orderJson!!.getInt(it) }
        return UserText(
            id = o.getString("id"),
            title = o.optString("title", ""),
            ordered = o.optBoolean("ordered", true),
            enabled = o.optBoolean("enabled", true),
            lines = lines,
            order = order,
            position = o.optInt("pos", -1)
        )
    }

    private fun save(context: Context) {
        val list = cache ?: return
        val array = JSONArray()
        for (t in list) {
            array.put(
                JSONObject()
                    .put("id", t.id).put("title", t.title).put("ordered", t.ordered)
                    .put("enabled", t.enabled).put("pos", t.position)
                    .put("order", JSONArray(t.order.toList()))
                    .put(
                        "lines",
                        JSONArray(t.lines.map { JSONObject().put("t", it.text).put("tr", it.translation) })
                    )
            )
        }
        File(context.applicationContext.filesDir, FILE).writeText(array.toString(), Charsets.UTF_8)
    }

    private fun newOrder(size: Int, ordered: Boolean): IntArray =
        if (ordered) IntArray(size) { it } else (0 until size).toMutableList().apply { shuffle() }.toIntArray()

    @Synchronized
    fun all(context: Context): List<UserText> = load(context).toList()

    @Synchronized
    fun get(context: Context, id: String): UserText? = load(context).firstOrNull { it.id == id }

    /** True when at least one enabled text has lines to show. */
    @Synchronized
    fun hasContent(context: Context): Boolean = load(context).any { it.enabled && it.lines.isNotEmpty() }

    /** Saves a new text. Returns it, or null when the library is full or [lines] is empty. */
    @Synchronized
    fun add(context: Context, title: String, lines: List<Line>, ordered: Boolean): UserText? {
        val list = load(context)
        if (lines.isEmpty() || list.size >= MAX_TEXTS) return null
        val text = UserText(
            id = UUID.randomUUID().toString(), title = title.trim(), ordered = ordered, enabled = true,
            lines = lines, order = newOrder(lines.size, ordered), position = -1
        )
        list.add(text)
        save(context)
        return text
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val list = load(context)
        if (list.removeAll { it.id == id }) {
            if (AppPrefs.activeTextId(context) == id) AppPrefs.setActiveTextId(context, null)
            save(context)
        }
    }

    @Synchronized
    fun setEnabled(context: Context, id: String, enabled: Boolean) = update(context, id) { it.copy(enabled = enabled) }

    /** Switches between in-order and shuffled and restarts the text's sequence. */
    @Synchronized
    fun setOrdered(context: Context, id: String, ordered: Boolean) = update(context, id) {
        it.copy(ordered = ordered, order = newOrder(it.lines.size, ordered), position = -1)
    }

    @Synchronized
    fun setTranslation(context: Context, id: String, lineIndex: Int, translation: String) = update(context, id) { t ->
        t.copy(lines = t.lines.mapIndexed { i, l -> if (i == lineIndex) l.copy(translation = translation) else l })
    }

    /** Adds a word to the "Saved words" text (created on first use). Returns false if it was already there. */
    @Synchronized
    fun saveWord(context: Context, word: String, translation: String): Boolean {
        val list = load(context)
        val existing = list.firstOrNull { it.id == SAVED_WORDS_ID }
        val clean = word.trim()
        if (clean.isEmpty()) return false
        if (existing != null && existing.lines.any { it.text.equals(clean, ignoreCase = true) }) return false
        val line = Line(clean, translation.trim())
        if (existing == null) {
            list.add(
                UserText(SAVED_WORDS_ID, "Saved words", false, true, listOf(line), newOrder(1, false), -1)
            )
        } else {
            val lines = existing.lines + line
            // Slot the new word near the front of what's left, so it comes up soon.
            val order = existing.order.toMutableList()
            val at = (existing.position + 1).coerceIn(0, order.size)
            order.add(at, lines.lastIndex)
            val index = list.indexOf(existing)
            list[index] = existing.copy(lines = lines, order = order.toIntArray())
        }
        save(context)
        return true
    }

    private fun update(context: Context, id: String, change: (UserText) -> UserText) {
        val list = load(context)
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        list[index] = change(list[index])
        save(context)
    }

    private fun toWord(t: UserText, line: Line, shown: Int): GreWord = GreWord(
        word = line.text,
        pos = t.title.ifBlank { "my text" },
        meaning = line.translation,
        example = "",
        sentence = true,
        footer = "${t.title.ifBlank { "My text" }} · $shown/${t.lines.size}",
        speakLang = "es-ES"
    )

    /** The line currently on screen, or null when no text is available. */
    @Synchronized
    fun current(context: Context): GreWord? {
        val list = load(context)
        val t = list.firstOrNull { it.id == AppPrefs.activeTextId(context) && it.enabled && it.lines.isNotEmpty() }
            ?: return advance(context)
        if (t.position < 0 || t.order.size != t.lines.size) return advance(context)
        return toWord(t, t.lines[t.order[t.position]], t.position + 1)
    }

    /** Next line of the active text; when it finishes, the next enabled text takes over. */
    @Synchronized
    fun advance(context: Context): GreWord? {
        val list = load(context)
        val enabled = list.filter { it.enabled && it.lines.isNotEmpty() }
        if (enabled.isEmpty()) return null

        var t = enabled.firstOrNull { it.id == AppPrefs.activeTextId(context) } ?: enabled.first()
        var order = t.order
        var position = t.position
        if (order.size != t.lines.size) {
            order = newOrder(t.lines.size, t.ordered)
            position = -1
        }
        position++
        if (position >= order.size) {
            // Finished: hand over to the next enabled text (or restart this one if it is the only one).
            val next = enabled[(enabled.indexOf(t) + 1) % enabled.size]
            persist(context, list, t, newOrder(t.lines.size, t.ordered), -1)
            t = next
            order = if (t.order.size == t.lines.size && t.position >= 0) t.order else newOrder(t.lines.size, t.ordered)
            position = 0
        }
        t = persist(context, list, t, order, position)
        AppPrefs.setActiveTextId(context, t.id)
        return toWord(t, t.lines[order[position]], position + 1)
    }

    private fun persist(context: Context, list: MutableList<UserText>, t: UserText, order: IntArray, position: Int): UserText {
        val updated = t.copy(order = order, position = position)
        val index = list.indexOfFirst { it.id == t.id }
        if (index >= 0) list[index] = updated
        save(context)
        return updated
    }
}
