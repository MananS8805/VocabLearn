package com.example.walllearn.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Words fetched from the web, kept on the device so they are searchable and
 * readable offline afterwards, exactly like WordNet words. Stored beside the
 * user's own words because the bundled dictionary database is read-only.
 */
object WebWordStore {

    private const val TAG = "WebWordStore"
    private const val FILE = "web_words.json"

    private var cache: MutableMap<String, Dictionary.Entry>? = null

    @Synchronized
    private fun load(context: Context): MutableMap<String, Dictionary.Entry> {
        cache?.let { return it }
        val map = java.util.TreeMap<String, Dictionary.Entry>()
        val file = File(context.applicationContext.filesDir, FILE)
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText(Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val senses = o.getJSONArray("senses")
                    val list = (0 until senses.length()).map {
                        val s = senses.getJSONObject(it)
                        Dictionary.Sense(s.optString("pos"), s.getString("def"), s.optString("ex"))
                    }
                    val word = o.getString("word")
                    map[word.lowercase()] = Dictionary.Entry(word, list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read $FILE", e)
            }
        }
        cache = map
        return map
    }

    @Synchronized
    fun get(context: Context, word: String): Dictionary.Entry? = load(context)[word.trim().lowercase()]

    @Synchronized
    fun contains(context: Context, word: String): Boolean = get(context, word) != null

    @Synchronized
    fun search(context: Context, prefix: String, limit: Int = 20): List<String> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return load(context).values.filter { it.word.lowercase().startsWith(p) }.map { it.word }.take(limit)
    }

    @Synchronized
    fun save(context: Context, entry: Dictionary.Entry) {
        val map = load(context)
        map[entry.word.lowercase()] = entry
        val arr = JSONArray()
        for (e in map.values) {
            arr.put(
                JSONObject().put("word", e.word).put(
                    "senses",
                    JSONArray(e.senses.map {
                        JSONObject().put("pos", it.pos).put("def", it.definition).put("ex", it.example)
                    })
                )
            )
        }
        File(context.applicationContext.filesDir, FILE).writeText(arr.toString(), Charsets.UTF_8)
    }
}
