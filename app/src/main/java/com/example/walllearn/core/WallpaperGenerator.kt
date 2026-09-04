package com.example.walllearn.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.example.walllearn.R
import com.example.walllearn.model.GreWord
import kotlin.math.roundToInt

/**
 * Renders a [GreWord] onto a full-screen wallpaper bitmap: a dark
 * gradient background with the word, its part of speech, definition,
 * and an example sentence laid out to stay clear of the lock screen
 * clock at the top of the display.
 */
object WallpaperGenerator {

    // Android has no built-in Georgia; the system serif face is the closest
    // available stand-in. Drop a licensed Georgia.ttf into res/font and swap
    // this for Typeface.createFromAsset/ResourcesCompat.getFont to use the
    // real thing.
    private val GEORGIA_FALLBACK = Typeface.SERIF

    fun generate(context: Context, word: GreWord, progress: Pair<Int, Int>): Bitmap {
        val (width, height) = screenSize(context)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(context, canvas, width, height)

        val marginX = (width * 0.09f)
        val contentWidth = (width - marginX * 2).roundToInt()

        // Leave room at the top for the lock screen clock/date widget most
        // Android versions draw automatically.
        var cursorY = height * 0.46f

        // Part of speech badge (skipped when the word was added without one)
        if (word.pos.isNotBlank()) {
            val posPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.wall_accent)
                textSize = width * 0.032f
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                letterSpacing = 0.12f
            }
            val posText = word.pos.uppercase()
            canvas.drawText(posText, marginX, cursorY, posPaint)
            cursorY += posPaint.textSize * 1.1f
        }

        // The word itself: white, Georgia (falls back to the system serif
        // face, since Android has no built-in Georgia), large and bold.
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.wall_word)
            textSize = width * 0.115f
            typeface = Typeface.create(GEORGIA_FALLBACK, Typeface.BOLD)
        }
        val wordLayout = StaticLayout.Builder
            .obtain(word.word, 0, word.word.length, wordPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .build()
        canvas.save()
        canvas.translate(marginX, cursorY)
        wordLayout.draw(canvas)
        canvas.restore()
        cursorY += wordLayout.height + wordPaint.textSize * 0.5f

        // Meaning
        val meaningPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.wall_meaning)
            textSize = width * 0.048f
            typeface = Typeface.DEFAULT
        }
        val meaningLayout = StaticLayout.Builder
            .obtain(word.meaning, 0, word.meaning.length, meaningPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(width * 0.012f, 1.0f)
            .build()
        canvas.save()
        canvas.translate(marginX, cursorY)
        meaningLayout.draw(canvas)
        canvas.restore()
        cursorY += meaningLayout.height + wordPaint.textSize * 0.55f

        // Example sentence, italic (skipped when the word was added without one)
        if (word.example.isNotBlank()) {
            val examplePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.wall_example)
                textSize = width * 0.038f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            val exampleText = "“${word.example}”"
            val exampleLayout = StaticLayout.Builder
                .obtain(exampleText, 0, exampleText.length, examplePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(width * 0.01f, 1.0f)
                .build()
            canvas.save()
            canvas.translate(marginX, cursorY)
            exampleLayout.draw(canvas)
            canvas.restore()
        }

        // Progress counter + watermark near the bottom
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.wall_example)
            alpha = 170
            textSize = width * 0.03f
            typeface = Typeface.DEFAULT
        }
        val footerText = context.getString(R.string.progress_format, progress.first, progress.second)
        canvas.drawText(footerText, marginX, height * 0.94f, footerPaint)

        val brandPaint = TextPaint(footerPaint).apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText("WallLearn", width - marginX, height * 0.94f, brandPaint)

        return bitmap
    }

    private fun drawBackground(context: Context, canvas: Canvas, width: Int, height: Int) {
        canvas.drawColor(Color.BLACK)

        // A subtle accent line to separate the word block visually.
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.wall_accent)
            alpha = 200
            strokeWidth = width * 0.01f
        }
        val marginX = width * 0.09f
        canvas.drawLine(marginX, height * 0.42f, marginX + width * 0.14f, height * 0.42f, accentPaint)
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
