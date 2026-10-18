package com.example.walllearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against a crash found on a real phone: Material's bottom bar resolves some theme
 * attributes (android:textColorSecondary, for one) by resource id, so an inline hex value
 * such as #E6F1E9D8 inflates with "Resource ID #0x0 not found". Theme items must point at
 * a @color resource instead.
 */
class ThemeResourcesTest {

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val themes = File("src/main/res/values/themes.xml").readText(Charsets.UTF_8)
    private val items = Regex("""<item name="([^"]+)">([^<]*)</item>""").findAll(themes).toList()

    @Test
    fun noThemeItemUsesAnInlineColour() {
        val literal = items.filter { it.groupValues[2].trim().startsWith("#") }
        assertTrue(
            "themes.xml has inline colours (use @color resources): " +
                literal.joinToString { "${it.groupValues[1]}=${it.groupValues[2]}" },
            literal.isEmpty()
        )
    }

    @Test
    fun threeAppLooksDefineTheSameTokens() {
        val styles = Regex("""<style name="(Theme\.Glossa[^"]*)"[^>]*>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(themes).associate { m ->
                m.groupValues[1] to Regex("""<item name="(glossa[^"]*)"""").findAll(m.groupValues[2]).map { it.groupValues[1] }.toSet()
            }
        assertEquals(setOf("Theme.Glossa", "Theme.Glossa.Light", "Theme.Glossa.Bauhaus"), styles.keys)
        val expected = styles.getValue("Theme.Glossa")
        assertTrue(expected.size >= 10)
        for ((name, tokens) in styles) assertEquals("$name is missing tokens", expected, tokens)
    }
}
