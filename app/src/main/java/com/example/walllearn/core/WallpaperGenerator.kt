package com.example.walllearn.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.walllearn.R
import com.example.walllearn.model.Face
import com.example.walllearn.model.GreWord
import com.example.walllearn.model.LockTheme
import kotlin.math.roundToInt

/**
 * Renders a [GreWord] onto a full-screen wallpaper bitmap in the look described
 * by a [LockTheme]: background, the word, its part of speech, definition, and an
 * example sentence, laid out to stay clear of the lock screen clock at the top.
 *
 * The same code draws the real wallpaper and the Themes screen's previews, so a
 * preview is exactly what will be applied.
 */
object WallpaperGenerator {

    fun generate(context: Context, word: GreWord, progress: Pair<Int, Int>): Bitmap {
        val (width, height) = screenSize(context)
        return generate(context, word, progress, AppPrefs.lockTheme(context), width, height)
    }

    fun generate(
        context: Context,
        word: GreWord,
        progress: Pair<Int, Int>,
        theme: LockTheme,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, theme, width, height)

        val marginX = width * 0.09f
        val contentWidth = (width - marginX * 2).roundToInt()
        val align = if (theme.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL

        // Leave room at the top for the lock screen clock/date widget.
        var cursorY = height * 0.46f

        if (theme.accentRule) {
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accent
                strokeWidth = width * 0.01f
            }
            val len = width * 0.14f
            val x0 = if (theme.centered) (width - len) / 2f else marginX
            canvas.drawLine(x0, height * 0.42f, x0 + len, height * 0.42f, rule)
        }

        fun layout(text: CharSequence, paint: TextPaint, spacing: Float = 0f) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(align)
                .setLineSpacing(spacing, 1.0f)
                .build()

        fun draw(l: StaticLayout, y: Float) {
            canvas.save()
            canvas.translate(marginX, y)
            l.draw(canvas)
            canvas.restore()
        }

        // Part of speech as a small accent badge above the word (most themes)...
        val pos = word.pos.trim()
        if (!theme.posUnderWord && pos.isNotEmpty()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accent
                textSize = width * 0.032f
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                letterSpacing = 0.12f
            }
            val l = layout(pos.uppercase(), p)
            draw(l, cursorY)
            cursorY += p.textSize * 1.4f
        }

        // Shrinks [paint] until [text] fits in [maxHeight], so long sentences never run off the card.
        fun fit(text: CharSequence, paint: TextPaint, startSize: Float, minSize: Float, maxHeight: Float, spacing: Float = 0f): StaticLayout {
            paint.textSize = startSize
            var l = layout(text, paint, spacing)
            while (l.height > maxHeight && paint.textSize > minSize) {
                paint.textSize *= 0.94f
                l = layout(text, paint, spacing)
            }
            return l
        }
        val sentence = word.sentence

        // The word (or, for phrases and sentences, the text sized to fit).
        val shown = when {
            sentence -> word.word
            theme.terminal -> "$ define ${word.word.lowercase()}"
            theme.uppercaseWord -> word.word.uppercase()
            else -> word.word
        }
        val baseSize = when {
            sentence -> width * 0.072f
            theme.terminal -> width * 0.062f
            theme.uppercaseWord -> width * 0.095f
            else -> width * 0.115f
        }
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.word
            typeface = typeface(theme.headFace, bold = !theme.terminal && !sentence)
            letterSpacing = if (theme.uppercaseWord && !sentence) 0.04f else 0f
            textSize = baseSize
        }
        // Shrink until the longest word fits on one line instead of breaking mid-word.
        val longest = shown.split(' ').maxByOrNull { it.length }.orEmpty()
        while (wordPaint.measureText(longest) > contentWidth && wordPaint.textSize > baseSize * 0.4f) {
            wordPaint.textSize *= 0.94f
        }
        val wordLayout = if (sentence) {
            fit(shown, wordPaint, wordPaint.textSize, width * 0.04f, height * 0.25f, width * 0.01f)
        } else {
            layout(shown, wordPaint)
        }
        draw(wordLayout, cursorY)
        cursorY += wordLayout.height + wordPaint.textSize * 0.18f

        // ...or as quiet text under the word (Mentalist, Terminal).
        if (theme.posUnderWord && pos.isNotEmpty()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.example
                textSize = width * 0.036f
                typeface = typeface(theme.bodyFace, bold = false)
            }
            val label = if (theme.terminal) "# ${pos.lowercase()}" else "${pos.lowercase()}."
            val l = layout(label, p)
            draw(l, cursorY)
            cursorY += l.height + p.textSize * 0.3f
        }
        cursorY += wordPaint.textSize * 0.32f

        // Meaning.
        val meaningPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.meaning
            textSize = width * 0.048f
            typeface = typeface(theme.bodyFace, bold = false, italic = theme.italicMeaning)
        }
        if (word.meaning.isNotBlank()) {
            val meaningLayout = if (sentence) {
                fit(word.meaning, meaningPaint, width * 0.05f, width * 0.034f, height * 0.15f, width * 0.012f)
            } else {
                layout(word.meaning, meaningPaint, width * 0.012f)
            }
            draw(meaningLayout, cursorY)
            cursorY += meaningLayout.height + wordPaint.textSize * 0.55f
        }

        // Example sentence (skipped when the word was added without one).
        if (word.example.isNotBlank()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.example
                textSize = width * 0.038f
                typeface = typeface(theme.bodyFace, bold = false, italic = true)
            }
            // Spanish hints on phrase cards are plain notes; real examples (English or Spanish) are quoted.
            val hasTranslation = word.exampleTranslation.isNotBlank()
            val isNote = sentence && !hasTranslation
            val example = if (isNote) word.example else "“${word.example}”"
            val exampleLayout = if (isNote || hasTranslation) {
                fit(example, p, width * 0.04f, width * 0.028f, height * (if (hasTranslation) 0.07f else 0.09f), width * 0.01f)
            } else {
                layout(example, p, width * 0.01f)
            }
            draw(exampleLayout, cursorY)
            // The example's translation sits right under it: same slot, quieter, not italic.
            if (hasTranslation) {
                cursorY += exampleLayout.height + width * 0.014f
                val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.example
                    alpha = 200
                    typeface = typeface(theme.bodyFace, bold = false)
                }
                draw(fit(word.exampleTranslation, t, width * 0.036f, width * 0.026f, height * 0.06f, width * 0.01f), cursorY)
            }
        }

        // Progress counter and watermark near the bottom.
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.example
            alpha = 170
            textSize = width * 0.03f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(
            word.footer ?: context.getString(R.string.progress_format, progress.first, progress.second),
            marginX, height * 0.94f, footer
        )
        val brand = TextPaint(footer).apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText("Glossa", width - marginX, height * 0.94f, brand)

        return bitmap
    }

    private fun typeface(face: Face, bold: Boolean, italic: Boolean = false): Typeface {
        val base = when (face) {
            Face.SERIF -> Typeface.SERIF
            Face.SANS -> Typeface.SANS_SERIF
            Face.MONO -> Typeface.MONOSPACE
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    private fun drawBackground(canvas: Canvas, theme: LockTheme, width: Int, height: Int) {
        if (theme.bgTop == theme.bgBottom && theme.bgMid == null) {
            canvas.drawColor(theme.bgTop)
            if (theme.shapes) drawBauhausShapes(canvas, width, height)
            return
        }
        val colors = if (theme.bgMid != null) {
            intArrayOf(theme.bgTop, theme.bgMid, theme.bgBottom)
        } else {
            intArrayOf(theme.bgTop, theme.bgBottom)
        }
        val stops = if (theme.bgMid != null) floatArrayOf(0f, 0.55f, 1f) else null
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /** Red circle off the top-right corner, yellow square, blue triangle; all above the word block. */
    private fun drawBauhausShapes(canvas: Canvas, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFFD9352A.toInt()
        canvas.drawCircle(w * 0.90f, h * 0.14f, w * 0.26f, paint)

        paint.color = 0xFFF2B705.toInt()
        val side = w * 0.17f
        canvas.drawRect(w * 0.07f, h * 0.27f, w * 0.07f + side, h * 0.27f + side, paint)

        paint.color = 0xFF2A5DB8.toInt()
        val baseW = w * 0.24f
        val x = w * 0.80f
        val top = h * 0.29f
        val path = android.graphics.Path().apply {
            moveTo(x - baseW / 2f, top + baseW * 0.87f)
            lineTo(x + baseW / 2f, top + baseW * 0.87f)
            lineTo(x, top)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                val w = bounds.width()
                val h = bounds.height()
                if (w > 0 && h > 0) return Pair(w, h)
            } else {
                @Suppress("DEPRECATION")
                val display = wm.defaultDisplay
                if (display != null) {
                    val dm = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    display.getRealMetrics(dm)
                    if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                        return Pair(dm.widthPixels, dm.heightPixels)
                    }
                }
            }
        }
        val dm: DisplayMetrics = context.resources.displayMetrics
        return Pair(dm.widthPixels, dm.heightPixels)
    }
}
