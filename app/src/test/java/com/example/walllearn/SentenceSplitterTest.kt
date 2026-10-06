package com.example.walllearn

import com.example.walllearn.core.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    private fun texts(p: String) = SentenceSplitter.split(p).map { it.text }

    @Test
    fun splitsOnTerminalPunctuation() {
        assertEquals(
            listOf("It rained.", "Was it cold?", "Very!"),
            texts("It rained. Was it cold? Very!")
        )
    }

    @Test
    fun doesNotSplitOnAbbreviations() {
        assertEquals(
            listOf("Use tools, e.g. a hammer, here.", "Then stop."),
            texts("Use tools, e.g. a hammer, here. Then stop.")
        )
        assertEquals(1, texts("The U.S. Senate agreed.").size)
        assertEquals(1, texts("Ask Dr. Smith about it.").size)
    }

    @Test
    fun doesNotSplitOnInitialsOrDecimals() {
        assertEquals(1, texts("Written by J. Smith in 1999.").size)
        assertEquals(1, texts("It grew by 3.5 percent last year.").size)
    }

    @Test
    fun keepsClosingQuotesWithTheirSentence() {
        assertEquals(
            listOf("She said “stop.”", "Nobody did."),
            texts("She said “stop.” Nobody did.")
        )
    }

    @Test
    fun doesNotSplitBeforeLowercase() {
        assertEquals(1, texts("He paused... and then continued.").size)
    }

    @Test
    fun emDashStaysInsideSentence() {
        assertEquals(1, texts("A far greater failure — an occasional slip.").size)
    }

    @Test
    fun recordsParagraphAndOffsets() {
        val paragraphs = listOf("One. Two.", "Three.")
        val s = SentenceSplitter.splitParagraphs(paragraphs)
        assertEquals(listOf(0, 0, 1), s.map { it.paragraph })
        assertEquals(listOf(0, 5, 0), s.map { it.start })
        for (x in s) assertEquals(x.text, paragraphs[x.paragraph].substring(x.start, x.start + x.text.length))
    }

    @Test
    fun handlesEmptyAndUnterminatedText() {
        assertTrue(texts("   ").isEmpty())
        assertEquals(listOf("No ending here"), texts("No ending here"))
    }

    @Test
    fun findsSentenceContainingQuote() {
        val paragraphs = listOf("First one. Second one is key. Third.")
        val s = SentenceSplitter.splitParagraphs(paragraphs)
        assertEquals(1, SentenceSplitter.indexOfQuote(s, paragraphs, "Second one is key."))
        assertEquals(1, SentenceSplitter.indexOfQuote(s, paragraphs, "is key"))
        assertEquals(-1, SentenceSplitter.indexOfQuote(s, paragraphs, "absent"))
    }
}
