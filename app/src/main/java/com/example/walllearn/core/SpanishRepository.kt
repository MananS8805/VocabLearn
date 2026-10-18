package com.example.walllearn.core

import android.content.Context
import android.util.Log
import com.example.walllearn.model.GreWord
import org.json.JSONArray

/**
 * The bundled Spanish course (assets/spanish_cards.json), served level by level.
 *
 * Each level is its own shuffled, no-repeat pass. In auto mode, finishing a pass moves
 * the learner up a level; in manual mode (or at the top level) the level reshuffles and
 * repeats. The learner can always jump to any level, in either mode. Per-level order and
 * position are kept in SharedPreferences under their own keys, so none of this touches
 * the English rotation's data.
 */
object SpanishRepository {

    private const val TAG = "SpanishRepository"
    private const val ASSET_PATH = "spanish_cards.json"

    data class Card(
        val level: Int,
        val kind: String,
        val es: String,
        val en: String,
        val note: String,
        /** Spanish example sentence and its English translation; blank for cards that are already sentences. */
        val example: String = "",
        val exampleTranslation: String = ""
    )

    val LEVEL_NAMES = listOf("Beginner", "Elementary", "Intermediate", "Advanced")
    val LEVEL_COUNT = LEVEL_NAMES.size

    @Volatile
    private var byLevel: Map<Int, List<Card>>? = null

    @Synchronized
    private fun cards(context: Context): Map<Int, List<Card>> {
        byLevel?.let { return it }
        val loaded = try {
            val text = context.applicationContext.assets.open(ASSET_PATH).use { it.readBytes().toString(Charsets.UTF_8) }
            parse(text)
        } catch (e: Exception) {
            Log.e(TAG, "Could not load $ASSET_PATH", e)
            emptyMap()
        }
        byLevel = loaded
        return loaded
    }

    internal fun parse(json: String): Map<Int, List<Card>> {
        val array = JSONArray(json)
        val all = ArrayList<Card>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            all.add(
                Card(
                    o.getInt("l"), o.getString("k"), o.getString("es"), o.getString("en"), o.optString("n", ""),
                    o.optString("ex", ""), o.optString("ext", "")
                )
            )
        }
        return all.groupBy { it.level }
    }

    fun levelSize(context: Context, level: Int): Int = cards(context)[level]?.size ?: 0

    /** True when there is a Spanish course to draw from. */
    fun isAvailable(context: Context): Boolean = cards(context).isNotEmpty()

    fun level(context: Context): Int = AppPrefs.spanishLevel(context).coerceIn(1, LEVEL_COUNT)

    fun isAuto(context: Context): Boolean = AppPrefs.spanishAuto(context)

    /** Jumps to [level]. Its pass starts fresh; other levels keep their saved place. */
    @Synchronized
    fun setLevel(context: Context, level: Int) {
        val target = level.coerceIn(1, LEVEL_COUNT)
        AppPrefs.setSpanishLevel(context, target)
        AppPrefs.setSpanishOrder(context, target, null, -1)
    }

    /** (cards seen in this pass, cards in this level). */
    fun levelProgress(context: Context): Pair<Int, Int> {
        val level = level(context)
        val total = levelSize(context, level)
        val position = AppPrefs.spanishPosition(context, level)
        return Pair((position + 1).coerceIn(0, total), total)
    }

    private fun readOrder(context: Context, level: Int, size: Int): IntArray? {
        val raw = AppPrefs.spanishOrder(context, level) ?: return null
        val order = try {
            raw.split(",").map { it.toInt() }.toIntArray()
        } catch (e: NumberFormatException) {
            return null
        }
        val valid = order.size == size && order.all { it in 0 until size } && order.distinct().size == size
        return if (valid) order else null
    }

    private fun shuffled(size: Int) = (0 until size).toMutableList().apply { shuffle() }.toIntArray()

    /** The card on screen now, without moving. Null when the course is missing. */
    @Synchronized
    fun current(context: Context): GreWord? {
        val level = level(context)
        val list = cards(context)[level] ?: return null
        val order = readOrder(context, level, list.size)
        val position = AppPrefs.spanishPosition(context, level)
        if (order == null || position < 0) return advance(context)
        return toWord(context, list[order[position.coerceAtMost(order.size - 1)]], level)
    }

    /** Moves to the next card, climbing a level in auto mode when a pass finishes. */
    @Synchronized
    fun advance(context: Context): GreWord? {
        var level = level(context)
        var list = cards(context)[level] ?: return null
        var order = readOrder(context, level, list.size)
        var position = AppPrefs.spanishPosition(context, level)
        if (order == null) {
            order = shuffled(list.size)
            position = -1
        }
        position++
        if (position >= order.size) {
            if (isAuto(context) && level < LEVEL_COUNT && cards(context)[level + 1]?.isNotEmpty() == true) {
                level++
                AppPrefs.setSpanishLevel(context, level)
                list = cards(context)[level]!!
            }
            order = shuffled(list.size)
            position = 0
        }
        AppPrefs.setSpanishOrder(context, level, order.joinToString(","), position)
        return toWord(context, list[order[position]], level)
    }

    private fun toWord(context: Context, card: Card, level: Int): GreWord {
        val (seen, total) = levelProgress(context)
        return GreWord(
            word = card.es,
            pos = "español · ${card.kind}",
            meaning = card.en,
            // Words show an example sentence with its translation; phrases and sentences show their hint.
            example = card.example.ifBlank { card.note },
            exampleTranslation = card.exampleTranslation,
            note = if (card.example.isNotBlank()) card.note else "",
            sentence = card.kind != "word" && card.kind != "verb" && card.kind != "tough word" && card.kind != "false friend",
            footer = "Level $level · ${LEVEL_NAMES[level - 1]} · $seen/$total",
            speakLang = "es-ES"
        )
    }
}
