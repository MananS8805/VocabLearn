package com.example.walllearn.core

import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks a word up on Wiktionary when it isn't in the bundled WordNet database.
 * Opt-in only: it runs when the user taps "Look up online", never in the background.
 * Blocking; call it off the main thread.
 */
object WebDictionary {

    private const val TAG = "WebDictionary"
    private const val BASE = "https://en.wiktionary.org/api/rest_v1/page/definition/"
    // Wikimedia asks API clients to identify themselves.
    private const val USER_AGENT = "Glossa/1.0 (Android vocabulary app; offline-first dictionary fallback)"
    private const val TIMEOUT_MS = 8000

    sealed interface Result {
        data class Found(val entry: Dictionary.Entry) : Result
        data object NotFound : Result
        /** No network, timeout, or the service failed. */
        data object Unavailable : Result
    }

    private val VALID_WORD = Regex("^[\\p{L}][\\p{L}'\\- ]{0,40}$")

    fun isLookupable(query: String): Boolean = VALID_WORD.matches(query.trim())

    fun lookup(rawWord: String): Result {
        val word = rawWord.trim().lowercase()
        if (!isLookupable(word)) return Result.NotFound
        val url = URL(BASE + URLEncoder.encode(word, "UTF-8").replace("+", "%20"))
        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                when (conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        WiktionaryParser.parse(word, body)?.let { Result.Found(it) } ?: Result.NotFound
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> Result.NotFound
                    else -> {
                        Log.w(TAG, "Unexpected HTTP ${conn.responseCode} for $word")
                        Result.Unavailable
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: FileNotFoundException) {
            Result.NotFound
        } catch (e: IOException) {
            Log.w(TAG, "Lookup failed for $word", e)
            Result.Unavailable
        }
    }
}
