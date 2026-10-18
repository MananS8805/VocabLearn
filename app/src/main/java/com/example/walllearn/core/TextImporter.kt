package com.example.walllearn.core

/**
 * Turns pasted text (prose, or lyrics-style text with one phrase per line) into
 * card-sized pieces. Pure Kotlin so it is unit-tested on the JVM.
 */
object TextImporter {

    enum class Mode { AUTO, LINES, SENTENCES }

    /** Cards longer than this are broken at a clause boundary so they stay readable on the lock screen. */
    const val MAX_CARD_CHARS = 140
    private const val MIN_PIECE_CHARS = 25
    private const val SHORT_LINE_CHARS = 10
    const val MAX_CARDS = 300

    private val SECTION_HEADER = Regex("^[\\[(].{1,40}[\\])]$")
    private val CLAUSE_BREAKS = listOf(", ", "; ", ": ", " — ", " - ", " y ", " pero ", " porque ", " que ", " et ", " mais ")

    /** Which mode [split] uses for [raw] when asked for [Mode.AUTO]. */
    fun detectMode(raw: String): Mode {
        val lines = cleanLines(raw)
        if (lines.size < 3) return Mode.SENTENCES
        val averageLength = lines.sumOf { it.length } / lines.size
        val punctuated = lines.count { it.last() in ".!?" }
        return if (averageLength < 70 && punctuated * 2 < lines.size) Mode.LINES else Mode.SENTENCES
    }

    fun split(raw: String, mode: Mode = Mode.AUTO): List<String> {
        val chosen = if (mode == Mode.AUTO) detectMode(raw) else mode
        val pieces = if (chosen == Mode.LINES) splitLines(raw) else splitSentences(raw)
        return dedupe(pieces.flatMap { breakLong(it) }).take(MAX_CARDS)
    }

    private fun cleanLines(raw: String): List<String> =
        raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !SECTION_HEADER.matches(it) && it.any(Char::isLetter) }

    /** One card per line; very short lines ("Oh, oh") are folded into the line after them. */
    private fun splitLines(raw: String): List<String> {
        val lines = cleanLines(raw)
        val out = ArrayList<String>()
        var i = 0
        while (i < lines.size) {
            var line = lines[i]
            while (line.length < SHORT_LINE_CHARS && i + 1 < lines.size &&
                line.length + 1 + lines[i + 1].length <= MAX_CARD_CHARS
            ) {
                i++
                line = "$line ${lines[i]}"
            }
            out.add(line)
            i++
        }
        return out
    }

    /** Paragraphs (blank-line separated) are re-flowed and cut at sentence ends. */
    private fun splitSentences(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = normalized.split(Regex("\n\\s*\n")).map { p ->
            p.lines().map { it.trim() }.filter { it.isNotEmpty() && !SECTION_HEADER.matches(it) }.joinToString(" ")
        }.filter { it.any(Char::isLetter) }
        return paragraphs.flatMap { SentenceSplitter.split(it).map { s -> s.text } }
    }

    /** Splits [text] near its middle at the best clause break until every piece fits. */
    fun breakLong(text: String): List<String> {
        val t = text.trim()
        if (t.length <= MAX_CARD_CHARS) return listOf(t)
        val middle = t.length / 2
        var bestAt = -1
        var bestDistance = Int.MAX_VALUE
        for (mark in CLAUSE_BREAKS) {
            var from = 0
            while (true) {
                val at = t.indexOf(mark, from)
                if (at < 0) break
                from = at + 1
                // Break after punctuation marks, before conjunctions.
                val cut = if (mark.first() == ' ') at else at + mark.trim().length
                if (cut < MIN_PIECE_CHARS || t.length - cut < MIN_PIECE_CHARS) continue
                val distance = kotlin.math.abs(cut - middle)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestAt = cut
                }
            }
        }
        if (bestAt < 0) {
            // No clause boundary: cut at the space nearest the middle.
            val space = t.lastIndexOf(' ', middle).takeIf { it >= MIN_PIECE_CHARS } ?: t.indexOf(' ', middle)
            if (space < 0) return listOf(t)
            bestAt = space
        }
        val head = t.substring(0, bestAt).trim().trimEnd(',', ';', ':')
        val tail = t.substring(bestAt).trim()
        return breakLong(head) + breakLong(tail)
    }

    /** Drops repeats (choruses) case-insensitively, keeping the first occurrence. */
    private fun dedupe(pieces: List<String>): List<String> {
        val seen = HashSet<String>()
        return pieces.filter { p ->
            val key = p.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()
            key.isNotEmpty() && seen.add(key)
        }
    }
}
