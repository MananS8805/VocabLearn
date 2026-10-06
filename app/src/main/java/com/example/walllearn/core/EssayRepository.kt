package com.example.walllearn.core

import android.content.Context
import android.util.Log
import com.example.walllearn.model.Essay
import com.example.walllearn.model.EssayPoint
import com.example.walllearn.model.GlossaryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The bundled model essays (assets/essays.json, produced by tools/build_essays.py)
 * plus essays the user pasted in, which live in a writable file next to
 * user_words.json because the APK's assets are read-only.
 */
object EssayRepository {

    private const val TAG = "EssayRepository"
    private const val ASSET_PATH = "essays.json"
    private const val USER_ESSAYS_FILE = "user_essays.json"

    @Volatile
    private var bundled: List<Essay>? = null

    /** Model essays first (in source order), then the user's own, newest last. */
    fun all(context: Context): List<Essay> = bundled(context) + loadUser(context)

    fun byId(context: Context, id: String): Essay? = all(context).firstOrNull { it.id == id }

    @Synchronized
    private fun bundled(context: Context): List<Essay> {
        bundled?.let { return it }
        val loaded = context.applicationContext.assets.open(ASSET_PATH).use {
            parseEssays(it.readBytes().toString(Charsets.UTF_8), isUser = false)
        }
        Log.i(TAG, "Loaded ${loaded.size} bundled essays")
        bundled = loaded
        return loaded
    }

    private fun loadUser(context: Context): List<Essay> {
        val file = File(context.applicationContext.filesDir, USER_ESSAYS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            parseEssays(file.readText(Charsets.UTF_8), isUser = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $USER_ESSAYS_FILE", e)
            emptyList()
        }
    }

    /**
     * Saves a pasted essay. Blank lines separate paragraphs; if the text has none,
     * each line is a paragraph. Returns the new essay, or null if [body] is blank.
     */
    @Synchronized
    fun addUserEssay(context: Context, title: String, prompt: String, body: String): Essay? {
        val paragraphs = splitIntoParagraphs(body)
        if (paragraphs.isEmpty()) return null
        val essay = Essay(
            id = "u" + System.currentTimeMillis(),
            theme = USER_THEME,
            title = title.trim().ifEmpty { paragraphs.first().take(40) },
            instructionType = "",
            prompt = prompt.trim(),
            paragraphs = paragraphs,
            isUser = true
        )
        persistUser(context, loadUser(context) + essay)
        return essay
    }

    @Synchronized
    fun deleteUserEssay(context: Context, id: String) {
        persistUser(context, loadUser(context).filter { it.id != id })
    }

    private fun persistUser(context: Context, essays: List<Essay>) {
        File(context.applicationContext.filesDir, USER_ESSAYS_FILE)
            .writeText(toJson(essays), Charsets.UTF_8)
    }

    const val USER_THEME = "Your essays"

    fun splitIntoParagraphs(body: String): List<String> {
        val normalized = body.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        val chunks = if (normalized.contains(Regex("\n\\s*\n"))) {
            normalized.split(Regex("\n\\s*\n"))
        } else {
            // No blank lines: a line break ends a paragraph only after a finished sentence,
            // so text hard-wrapped by a PDF or email isn't chopped mid-sentence.
            val lines = ArrayList<String>()
            for (line in normalized.split("\n").map { it.trim() }.filter { it.isNotEmpty() }) {
                val prev = lines.lastOrNull()
                if (prev != null && !prev.matches(Regex(".*[.!?][\"'\u201D\u2019)]*$"))) {
                    lines[lines.size - 1] = "$prev $line"
                } else {
                    lines.add(line)
                }
            }
            lines
        }
        return chunks.map { it.replace(Regex("\\s*\n\\s*"), " ").trim() }.filter { it.isNotEmpty() }
    }

    fun parseEssays(jsonText: String, isUser: Boolean): List<Essay> {
        val root = JSONObject(jsonText)
        val array = root.getJSONArray("essays")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Essay(
                id = o.getString("id"),
                theme = o.optString("theme", USER_THEME),
                title = o.getString("title"),
                instructionType = o.optString("instructionType", ""),
                prompt = o.optString("prompt", ""),
                paragraphs = o.getJSONArray("paragraphs").strings(),
                glossary = o.optJSONArray("glossary").objects().map {
                    GlossaryEntry(it.getString("term"), it.getString("def"))
                },
                structureNote = o.optString("structureNote", ""),
                points = o.optJSONArray("annotations").objects().map {
                    EssayPoint(it.getString("kind"), it.getString("quote"), it.optString("why", ""))
                },
                isUser = isUser
            )
        }
    }

    fun toJson(essays: List<Essay>): String {
        val array = JSONArray()
        for (e in essays) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("theme", e.theme)
                    .put("title", e.title)
                    .put("instructionType", e.instructionType)
                    .put("prompt", e.prompt)
                    .put("paragraphs", JSONArray(e.paragraphs))
            )
        }
        return JSONObject().put("version", 1).put("essays", array).toString()
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
