package com.example.walllearn.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * The offline English dictionary (~147,000 words from Princeton WordNet 3.0).
 *
 * It ships as a prebuilt SQLite database in assets/dictionary.db (built by
 * tools/build_dictionary.py). SQLite can't open a file inside the APK, so the
 * first lookup copies it into the app's databases folder; it's re-copied only
 * when [DB_VERSION] changes. All calls hit disk - run them off the main thread.
 */
object Dictionary {

    private const val TAG = "Dictionary"
    private const val ASSET_NAME = "dictionary.db"

    /** Must match DB_VERSION in tools/build_dictionary.py. */
    private const val DB_VERSION = 1

    data class Sense(val pos: String, val definition: String, val example: String)

    data class Entry(val word: String, val senses: List<Sense>)

    @Volatile
    private var db: SQLiteDatabase? = null

    @Synchronized
    private fun open(context: Context): SQLiteDatabase {
        db?.let { return it }
        val file = context.applicationContext.getDatabasePath(ASSET_NAME)
        if (!file.exists() || installedVersion(file) != DB_VERSION) {
            copyFromAssets(context, file)
        }
        return SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).also { db = it }
    }

    private fun installedVersion(file: File): Int = try {
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).use { it.version }
    } catch (e: Exception) {
        Log.w(TAG, "Installed dictionary unreadable; replacing it", e)
        -1
    }

    private fun copyFromAssets(context: Context, target: File) {
        target.parentFile?.mkdirs()
        // Copy to a temp file first so a crash mid-copy never leaves a truncated DB behind.
        val temp = File(target.path + ".tmp")
        context.applicationContext.assets.open(ASSET_NAME).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        Log.i(TAG, "Installed dictionary v$DB_VERSION (${target.length() / 1024} KB)")
    }

    /** Words starting with [query] (case-insensitive), alphabetically, exact match first. */
    fun search(context: Context, query: String, limit: Int = 60): List<String> {
        val prefix = query.trim().lowercase()
        if (prefix.isEmpty()) return emptyList()
        // A range scan on the word index is a fast, index-friendly prefix match.
        return open(context).rawQuery(
            "SELECT word FROM words WHERE word >= ? AND word < ? ORDER BY word LIMIT ?",
            arrayOf(prefix, prefix + '￿', limit.toString())
        ).use { cursor ->
            List(cursor.count) { cursor.moveToNext(); cursor.getString(0) }
        }
    }

    /** All meanings of [word], most common first, or null if it isn't in the dictionary. */
    fun lookup(context: Context, word: String): Entry? {
        val key = word.trim().lowercase()
        val senses = open(context).rawQuery(
            """
            SELECT y.pos, y.definition, y.example
            FROM words w
            JOIN senses s ON s.word_id = w.id
            JOIN synsets y ON y.id = s.synset_id
            WHERE w.word = ?
            ORDER BY s.rank
            """.trimIndent(),
            arrayOf(key)
        ).use { cursor ->
            List(cursor.count) {
                cursor.moveToNext()
                Sense(posName(cursor.getString(0)), cursor.getString(1), cursor.getString(2))
            }
        }
        return if (senses.isEmpty()) null else Entry(key, senses)
    }

    private fun posName(code: String) = when (code) {
        "n" -> "noun"
        "v" -> "verb"
        "a" -> "adjective"
        "r" -> "adverb"
        else -> ""
    }
}
