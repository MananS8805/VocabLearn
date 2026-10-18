package com.example.walllearn

import com.example.walllearn.core.SpanishRepository
import com.example.walllearn.core.TextImporter
import com.example.walllearn.core.WeightedPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguageMixTest {

    private fun runPicks(weights: IntArray, count: Int): IntArray {
        val credits = IntArray(weights.size)
        val tally = IntArray(weights.size)
        repeat(count) { tally[WeightedPicker.pick(weights, credits)]++ }
        return tally
    }

    @Test
    fun pickerMatchesTheRatioExactly() {
        assertEquals(listOf(30, 10, 0), runPicks(intArrayOf(3, 1, 0), 40).toList())
        assertEquals(listOf(50, 50, 0), runPicks(intArrayOf(1, 1, 0), 100).toList())
    }

    @Test
    fun pickerSpreadsPicksInsteadOfClumping() {
        val credits = IntArray(2)
        val seq = List(8) { WeightedPicker.pick(intArrayOf(3, 1), credits) }
        // The minority source never waits more than three picks.
        assertTrue(seq.indices.all { i -> seq.subList(i, minOf(i + 4, seq.size)).let { it.size < 4 || 1 in it } })
    }

    @Test
    fun pickerReturnsMinusOneWhenEverythingIsOff() {
        assertEquals(-1, WeightedPicker.pick(intArrayOf(0, 0, 0), IntArray(3)))
    }

    @Test
    fun zeroWeightSourceNeverRuns() {
        assertEquals(0, runPicks(intArrayOf(1, 0, 2), 90)[1])
    }

    @Test
    fun linesModeKeepsOneCardPerLineAndDropsRepeats() {
        val raw = "[Verso 1]\nMe gusta caminar por la playa\nCuando sale el sol por la mañana\n\nMe gusta caminar por la playa\nY escuchar el mar tranquilo"
        val cards = TextImporter.split(raw, TextImporter.Mode.LINES)
        assertEquals(
            listOf(
                "Me gusta caminar por la playa",
                "Cuando sale el sol por la mañana",
                "Y escuchar el mar tranquilo"
            ),
            cards
        )
    }

    @Test
    fun autoDetectsLinesForUnpunctuatedShortLines() {
        val raw = "Voy caminando despacio\nPor la calle de mi barrio\nMirando todo el paisaje\nSin prisa y sin reloj"
        assertEquals(TextImporter.Mode.LINES, TextImporter.detectMode(raw))
    }

    @Test
    fun autoDetectsSentencesForProse() {
        val raw = "Hoy fui al mercado con mi madre. Compramos frutas y verduras frescas para la semana.\n" +
            "Después volvimos a casa y cocinamos juntos. Fue un día muy tranquilo y agradable."
        assertEquals(TextImporter.Mode.SENTENCES, TextImporter.detectMode(raw))
        val cards = TextImporter.split(raw)
        assertEquals(4, cards.size)
        assertEquals("Hoy fui al mercado con mi madre.", cards[0])
    }

    @Test
    fun spanishQuestionsSplitAtTheirBoundaries() {
        val cards = TextImporter.split("Estoy bien. ¿Y tú? ¡Qué bueno verte!", TextImporter.Mode.SENTENCES)
        assertEquals(listOf("Estoy bien.", "¿Y tú?", "¡Qué bueno verte!"), cards)
    }

    @Test
    fun veryShortLinesFoldIntoTheNextOne() {
        val cards = TextImporter.split("Oh oh\nNo me digas que no\nSiempre vuelvo a ti", TextImporter.Mode.LINES)
        assertEquals(listOf("Oh oh No me digas que no", "Siempre vuelvo a ti"), cards)
    }

    @Test
    fun longLinesBreakAtAClauseNearTheMiddle() {
        val long = "Cuando llegamos a la estación de tren de la ciudad vieja ya era muy tarde, " +
            "y todos los viajeros esperaban cansados y en silencio en el andén de la derecha"
        val pieces = TextImporter.breakLong(long)
        assertTrue(pieces.size >= 2)
        assertTrue(pieces.all { it.length <= TextImporter.MAX_CARD_CHARS })
        assertEquals(long.replace(",", "").replace(" ", ""), pieces.joinToString("").replace(" ", ""))
    }

    @Test
    fun spanishCourseAssetParsesAndCoversEveryLevel() {
        val json = File("src/main/assets/spanish_cards.json").readText(Charsets.UTF_8)
        val byLevel = SpanishRepository.parse(json)
        for (level in 1..SpanishRepository.LEVEL_COUNT) {
            assertTrue("level $level is empty", (byLevel[level]?.size ?: 0) >= 25)
        }
        val all = byLevel.values.flatten()
        assertTrue(all.all { it.es.isNotBlank() && it.en.isNotBlank() })
        assertEquals("duplicate Spanish cards", all.size, all.map { it.es.lowercase() }.distinct().size)
    }
}
