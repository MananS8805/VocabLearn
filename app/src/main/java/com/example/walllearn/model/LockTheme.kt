package com.example.walllearn.model

/** Which typeface family a theme uses for a text role. */
enum class Face { SERIF, SANS, MONO }

/**
 * One lock screen look. Colors are ARGB ints. A theme with [bgBottom] != [bgTop]
 * paints a vertical gradient (the optional [bgMid] adds a middle stop at 55%).
 */
data class LockTheme(
    val id: String,
    val name: String,
    val tagline: String,
    val bgTop: Int,
    val bgBottom: Int = bgTop,
    val bgMid: Int? = null,
    val word: Int,
    val meaning: Int,
    val example: Int,
    val accent: Int,
    val headFace: Face = Face.SERIF,
    val bodyFace: Face = Face.SANS,
    val uppercaseWord: Boolean = false,
    val centered: Boolean = false,
    val accentRule: Boolean = true,
    val italicMeaning: Boolean = false,
    /** Shows the word as a shell prompt ("$ define word") in the terminal theme. */
    val terminal: Boolean = false,
    /** Draws the part of speech as muted "adjective." under the word instead of an accent badge. */
    val posUnderWord: Boolean = false,
    /** Draws the Bauhaus circle, square and triangle behind the text. */
    val shapes: Boolean = false
) {
    companion object {
        const val DEFAULT_ID = "mentalist"
    }
}

/** The ten built-in themes, in the order shown on the Themes screen. */
object LockThemes {

    private fun c(hex: Long) = (0xFF000000L or hex).toInt()

    val all: List<LockTheme> = listOf(
        LockTheme(
            id = "mentalist", name = "Mentalist", tagline = "Pure black, white serif",
            bgTop = c(0x000000), word = c(0xFFFFFF), meaning = c(0xFFFFFF),
            example = c(0x9A9A9A), accent = c(0x9A9A9A),
            headFace = Face.SERIF, bodyFace = Face.SERIF,
            uppercaseWord = true, accentRule = false, posUnderWord = true
        ),
        LockTheme(
            id = "midnight", name = "Midnight ink", tagline = "Navy with a blue rule",
            bgTop = c(0x0B1020), word = c(0xFFFFFF), meaning = c(0x8F9BC7),
            example = c(0x8F9BC7), accent = c(0x7C9CFF)
        ),
        LockTheme(
            id = "parchment", name = "Parchment", tagline = "Light, warm paper",
            bgTop = c(0xF3EBDD), word = c(0x2B2118), meaning = c(0x5A4A3A),
            example = c(0x7A6A58), accent = c(0xA8442A), bodyFace = Face.SERIF
        ),
        LockTheme(
            id = "dusk", name = "Dusk", tagline = "The app's mountain sky",
            bgTop = c(0x141428), bgMid = c(0x4A3A6A), bgBottom = c(0xA0708A),
            word = c(0xFFFFFF), meaning = c(0xE1D6EE), example = c(0xE1D6EE),
            accent = c(0xFFD3A0)
        ),
        LockTheme(
            id = "forest", name = "Forest", tagline = "Deep green, mint",
            bgTop = c(0x0E2A22), word = c(0xE8FFF5), meaning = c(0x8FC9B2),
            example = c(0x8FC9B2), accent = c(0x5FE0B0)
        ),
        LockTheme(
            id = "ember", name = "Ember", tagline = "Black, burnt orange",
            bgTop = c(0x0C0907), word = c(0xFFF4EA), meaning = c(0xB99A86),
            example = c(0xB99A86), accent = c(0xE8743B), uppercaseWord = true
        ),
        LockTheme(
            id = "paper", name = "Paper minimal", tagline = "White, bold sans",
            bgTop = c(0xFAFAF7), word = c(0x111111), meaning = c(0x555555),
            example = c(0x777777), accent = c(0x111111),
            headFace = Face.SANS, centered = true, accentRule = false
        ),
        LockTheme(
            id = "terminal", name = "Terminal", tagline = "Black, green monospace",
            bgTop = c(0x050805), word = c(0x7DFF9A), meaning = c(0x4FA866),
            example = c(0x3E8A55), accent = c(0x7DFF9A),
            headFace = Face.MONO, bodyFace = Face.MONO,
            accentRule = false, terminal = true, posUnderWord = true
        ),
        LockTheme(
            id = "rose", name = "Rose quartz", tagline = "Plum, centred italic",
            bgTop = c(0x2A1A24), word = c(0xFFEEF4), meaning = c(0xD3A9BA),
            example = c(0xD3A9BA), accent = c(0xFF9FBD),
            bodyFace = Face.SERIF, centered = true, italicMeaning = true
        ),
        LockTheme(
            id = "ocean", name = "Ocean", tagline = "Teal, centred",
            bgTop = c(0x06242C), bgBottom = c(0x0B5563),
            word = c(0xF0FDFF), meaning = c(0xA4D6DF), example = c(0xA4D6DF),
            accent = c(0x6FE3F0), headFace = Face.SANS, centered = true
        ),
        LockTheme(
            id = "bauhaus", name = "Bauhaus", tagline = "Black with primary shapes",
            bgTop = c(0x0E0E0E), word = c(0xF1E9D8), meaning = c(0xF1E9D8),
            example = c(0xB5AC98), accent = c(0xF2B705),
            headFace = Face.SANS, accentRule = false, shapes = true
        )
    )

    fun byId(id: String?): LockTheme = all.firstOrNull { it.id == id } ?: all.first()
}
