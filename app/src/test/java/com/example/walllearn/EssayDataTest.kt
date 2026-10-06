package com.example.walllearn

import com.example.walllearn.core.EssayRepository
import com.example.walllearn.core.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the shipped assets/essays.json against the runtime code that consumes it. */
class EssayDataTest {

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val essays = EssayRepository.parseEssays(
        File("src/main/assets/essays.json").readText(Charsets.UTF_8), isUser = false
    )

    @Test
    fun shipsFortyEssaysInEightThemes() {
        assertEquals(40, essays.size)
        assertEquals(8, essays.map { it.theme }.toSet().size)
        assertEquals(essays.size, essays.map { it.id }.toSet().size)
    }

    @Test
    fun everyEssayHasProseAndNotes() {
        for (e in essays) {
            assertTrue("${e.id} prompt", e.prompt.isNotBlank())
            assertTrue("${e.id} paragraphs", e.paragraphs.size >= 4)
            assertTrue("${e.id} glossary", e.glossary.isNotEmpty())
            assertTrue("${e.id} note", e.structureNote.isNotBlank())
            assertTrue("${e.id} points", e.points.isNotEmpty())
        }
    }

    /** The build tool verified quotes with its own splitter; make sure the app's finds them too. */
    @Test
    fun everyPointQuoteIsFoundByTheAppsSplitter() {
        for (e in essays) {
            val sentences = SentenceSplitter.splitParagraphs(e.paragraphs)
            for (p in e.points) {
                assertTrue(
                    "${e.id}: quote not located: ${p.quote.take(50)}",
                    SentenceSplitter.indexOfQuote(sentences, e.paragraphs, p.quote) >= 0
                )
            }
        }
    }

    @Test
    fun sentenceCountsAreSane() {
        for (e in essays) {
            val n = SentenceSplitter.splitParagraphs(e.paragraphs).size
            assertTrue("${e.id}: $n sentences", n in 8..80)
        }
    }

    @Test
    fun userEssayJsonRoundTrips() {
        val original = essays.take(1).map { it.copy(isUser = true) }
        val back = EssayRepository.parseEssays(EssayRepository.toJson(original), isUser = true)
        assertEquals(original.first().title, back.first().title)
        assertEquals(original.first().paragraphs, back.first().paragraphs)
    }

    @Test
    fun pastedTextSplitsIntoParagraphs() {
        assertEquals(listOf("A b.", "C d."), EssayRepository.splitIntoParagraphs("A b.\n\nC d."))
        assertEquals(listOf("A b.", "C d."), EssayRepository.splitIntoParagraphs("A b.\nC d."))
        assertEquals(listOf("A b c."), EssayRepository.splitIntoParagraphs("A b\nc.\r\n\r\n  \n"))
        assertTrue(EssayRepository.splitIntoParagraphs("  \n ").isEmpty())
    }
}
