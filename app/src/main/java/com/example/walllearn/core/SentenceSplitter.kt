package com.example.walllearn.core

/**
 * Splits essay paragraphs into sentences so each can be spoken and highlighted
 * as one "lyric" line. Pure Kotlin (no Android types) so it is unit-tested on the JVM.
 *
 * A boundary is sentence-final punctuation (plus any closing quote/bracket), then
 * whitespace, then something that can start a sentence. A period after a known
 * abbreviation ("e.g.", "U.S.") or a single initial ("J. Smith") is not a boundary.
 */
object SentenceSplitter {

    /** [start] is the sentence's offset within its paragraph's text. */
    data class Sentence(val text: String, val paragraph: Int, val start: Int)

    private val ABBREVIATIONS = setOf(
        "e.g", "i.e", "u.s", "u.k", "vs", "dr", "mr", "mrs", "ms", "st", "no",
        "etc", "cf", "inc", "approx", "fig", "prof", "sr", "jr"
    )
    private const val CLOSERS = "\"'”’)]"
    private const val OPENERS = "\"'“‘(["

    fun splitParagraphs(paragraphs: List<String>): List<Sentence> =
        paragraphs.flatMapIndexed { i, p -> split(p, i) }

    fun split(paragraph: String, paragraphIndex: Int = 0): List<Sentence> {
        val out = ArrayList<Sentence>()
        var begin = 0
        var i = 0
        while (i < paragraph.length) {
            val c = paragraph[i]
            if (c == '.' || c == '!' || c == '?') {
                var end = i + 1
                while (end < paragraph.length && paragraph[end] in CLOSERS) end++
                if (isBoundary(paragraph, i, end)) {
                    addSentence(out, paragraph, begin, end, paragraphIndex)
                    begin = end
                }
                i = end
            } else {
                i++
            }
        }
        addSentence(out, paragraph, begin, paragraph.length, paragraphIndex)
        return out
    }

    private fun isBoundary(text: String, punctAt: Int, afterCloser: Int): Boolean {
        if (afterCloser >= text.length) return true
        if (!text[afterCloser].isWhitespace()) return false
        var next = afterCloser
        while (next < text.length && text[next].isWhitespace()) next++
        if (next >= text.length) return true
        val n = text[next]
        if (!(n.isUpperCase() || n.isDigit() || n in OPENERS)) return false
        if (text[punctAt] == '.' && afterCloser == punctAt + 1 && endsWithAbbreviation(text, punctAt)) {
            return false
        }
        return true
    }

    /** True if the token ending just before the '.' at [dotAt] is an abbreviation or an initial. */
    private fun endsWithAbbreviation(text: String, dotAt: Int): Boolean {
        var s = dotAt
        while (s > 0 && !text[s - 1].isWhitespace() && text[s - 1] !in OPENERS) s--
        val token = text.substring(s, dotAt).lowercase()
        if (token in ABBREVIATIONS) return true
        // A lone capital letter is an initial ("J. Smith"); a lone lowercase one is not.
        return token.length == 1 && text[s].isUpperCase()
    }

    private fun addSentence(out: MutableList<Sentence>, text: String, from: Int, to: Int, para: Int) {
        var s = from
        var e = to
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s < e) out.add(Sentence(text.substring(s, e), para, s))
    }

    /**
     * Index in [sentences] of the sentence that contains [quote], or -1. The quote may
     * start mid-sentence, so match on the paragraph text and pick the sentence that
     * covers the quote's start.
     */
    fun indexOfQuote(sentences: List<Sentence>, paragraphs: List<String>, quote: String): Int {
        for ((p, text) in paragraphs.withIndex()) {
            val at = text.indexOf(quote)
            if (at < 0) continue
            val covering = sentences.indexOfLast { it.paragraph == p && it.start <= at }
            if (covering >= 0) return covering
        }
        return -1
    }
}
