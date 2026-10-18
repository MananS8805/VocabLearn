package com.example.walllearn.model

/**
 * One card shown on the lock screen. Historically a GRE vocabulary entry: the word
 * itself, its part of speech, a concise definition, and one example sentence.
 *
 * The same shape also carries Spanish cards and imported-text lines, using the
 * optional fields: [word] is then the Spanish text, [meaning] its English
 * translation and [example] a short note. English words leave the extras at their
 * defaults, so existing saved words keep loading and rendering exactly as before.
 */
data class GreWord(
    val word: String,
    val pos: String,
    val meaning: String,
    val example: String,
    /** True when [word] is a whole phrase or sentence, so it is laid out to fit rather than as a headword. */
    val sentence: Boolean = false,
    /** Replaces the "Word n of N" footer when set (e.g. "Level 2 · 35/200"). */
    val footer: String? = null,
    /** BCP-47 tag to read [word] aloud in (e.g. "es-ES"), or null when there is nothing to speak. */
    val speakLang: String? = null
)
