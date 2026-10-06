package com.example.walllearn.core

import org.json.JSONObject

/**
 * Turns the JSON from Wiktionary's REST "definition" endpoint into a
 * [Dictionary.Entry]. Pure Kotlin so it can be unit-tested on the JVM.
 *
 * The response looks like {"en":[{"partOfSpeech":"Adjective","definitions":
 * [{"definition":"<span>by <a ...>serendipity</a></span>","examples":["..."]}]}]}
 * where definitions and examples are HTML fragments. Nothing is rewritten:
 * markup is removed and entities decoded, the words themselves are untouched.
 */
object WiktionaryParser {

    const val MAX_SENSES = 8

    /** Null when the response has no English entry with at least one usable definition. */
    fun parse(word: String, json: String): Dictionary.Entry? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        val entries = root.optJSONArray("en") ?: return null
        val senses = ArrayList<Dictionary.Sense>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val pos = entry.optString("partOfSpeech", "").trim().lowercase()
            val defs = entry.optJSONArray("definitions") ?: continue
            for (j in 0 until defs.length()) {
                val d = defs.optJSONObject(j) ?: continue
                val text = stripHtml(d.optString("definition", ""))
                if (text.isBlank()) continue
                val examples = d.optJSONArray("examples")
                val example = if (examples != null && examples.length() > 0) {
                    stripHtml(examples.optString(0, ""))
                } else {
                    ""
                }
                senses.add(Dictionary.Sense(pos, text, example))
                if (senses.size >= MAX_SENSES) break
            }
            if (senses.size >= MAX_SENSES) break
        }
        if (senses.isEmpty()) return null
        return Dictionary.Entry(word.trim().lowercase(), senses)
    }

    private val TAG = Regex("<[^>]*>")
    private val SPACES = Regex("\\s+")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

    fun stripHtml(html: String): String {
        var s = TAG.replace(html, "")
        s = NUMERIC_ENTITY.replace(s) {
            val code = it.groupValues[2].toIntOrNull(if (it.groupValues[1].isEmpty()) 10 else 16)
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else it.value
        }
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        return SPACES.replace(s, " ").trim()
    }
}
