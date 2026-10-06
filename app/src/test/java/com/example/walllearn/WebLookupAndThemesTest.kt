package com.example.walllearn

import com.example.walllearn.core.WebDictionary
import com.example.walllearn.core.WiktionaryParser
import com.example.walllearn.model.EssayVoices
import com.example.walllearn.model.LockThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLookupAndThemesTest {

    // Shape taken from a live response of en.wiktionary.org/api/rest_v1/page/definition/serendipitous.
    private val sample = """
        {"en":[{"partOfSpeech":"Adjective","language":"English","definitions":[
          {"definition":"<span>by <a rel=\"mw:WikiLink\" href=\"./serendipity\" title=\"serendipity\">serendipity</a>; by unexpected good fortune</span>",
           "examples":["His raise was <b>serendipitous</b> &amp; welcome."]},
          {"definition":"<span>pleasantly surprising</span>"}
        ]}],
         "fr":[{"partOfSpeech":"Adjectif","definitions":[{"definition":"ignored"}]}]}
    """.trimIndent()

    @Test
    fun parsesEnglishEntryAndStripsMarkup() {
        val e = WiktionaryParser.parse("Serendipitous", sample)!!
        assertEquals("serendipitous", e.word)
        assertEquals(2, e.senses.size)
        assertEquals("adjective", e.senses[0].pos)
        assertEquals("by serendipity; by unexpected good fortune", e.senses[0].definition)
        assertEquals("His raise was serendipitous & welcome.", e.senses[0].example)
        assertEquals("", e.senses[1].example)
    }

    @Test
    fun ignoresOtherLanguagesAndBadInput() {
        assertNull(WiktionaryParser.parse("x", """{"fr":[{"partOfSpeech":"Nom","definitions":[{"definition":"a"}]}]}"""))
        assertNull(WiktionaryParser.parse("x", "not json"))
        assertNull(WiktionaryParser.parse("x", """{"en":[{"partOfSpeech":"Noun","definitions":[{"definition":"<span></span>"}]}]}"""))
    }

    @Test
    fun capsSensesAndDecodesEntities() {
        val defs = (1..20).joinToString(",") { """{"definition":"sense $it"}""" }
        val e = WiktionaryParser.parse("w", """{"en":[{"partOfSpeech":"Noun","definitions":[$defs]}]}""")!!
        assertEquals(WiktionaryParser.MAX_SENSES, e.senses.size)
        // named, decimal and hex entities
        assertEquals("a < b é \"q\" & done", WiktionaryParser.stripHtml("a &lt; b &#233; &#x22;q&quot; &amp; done"))
    }

    @Test
    fun onlyPlausibleWordsAreLookedUp() {
        assertTrue(WebDictionary.isLookupable("selfie"))
        assertTrue(WebDictionary.isLookupable("  well-known "))
        assertTrue(WebDictionary.isLookupable("café"))
        assertFalse(WebDictionary.isLookupable(""))
        assertFalse(WebDictionary.isLookupable("a/b"))
        assertFalse(WebDictionary.isLookupable("../etc"))
        assertFalse(WebDictionary.isLookupable("x".repeat(60)))
        assertFalse(WebDictionary.isLookupable("123"))
    }

    @Test
    fun elevenDistinctThemes() {
        assertEquals(11, LockThemes.all.size)
        assertEquals(11, LockThemes.all.map { it.id }.toSet().size)
        assertTrue(LockThemes.byId("bauhaus").shapes)
        assertEquals(1, LockThemes.all.count { it.shapes })
        assertEquals("mentalist", LockThemes.all.first().id)
        assertEquals("mentalist", LockThemes.byId("nope").id)
        assertNotNull(LockThemes.byId("ocean"))
        // Text must stay readable against its own background (rough luminance contrast).
        for (t in LockThemes.all) {
            val bgMid = (lum(t.bgTop) + lum(t.bgBottom)) / 2
            assertTrue("${t.id} word contrast", kotlin.math.abs(lum(t.word) - bgMid) > 0.45)
            assertTrue("${t.id} meaning contrast", kotlin.math.abs(lum(t.meaning) - bgMid) > 0.3)
        }
    }

    private fun lum(c: Int): Double {
        val r = (c shr 16 and 0xFF) / 255.0
        val g = (c shr 8 and 0xFF) / 255.0
        val b = (c and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    @Test
    fun fiveVoicesWithUniqueIds() {
        assertEquals(5, EssayVoices.all.size)
        assertEquals(5, EssayVoices.all.map { it.id }.toSet().size)
        assertEquals(setOf("US", "GB", "IN"), EssayVoices.all.map { it.country }.toSet())
        assertEquals(EssayVoices.DEFAULT_ID, EssayVoices.byId(null).id)
    }
}
