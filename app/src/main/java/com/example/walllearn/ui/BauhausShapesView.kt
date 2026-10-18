package com.example.walllearn.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * The Bauhaus theme's backdrop: a red circle bleeding off the top-right corner, a yellow
 * square, and a blue triangle, all flat primary colours on the black window background.
 * Static (no animation) and sized from the view width so it looks the same on any phone.
 */
class BauhausShapesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triangle = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()

        paint.color = RED
        canvas.drawCircle(w * 0.90f, w * 0.34f, w * 0.26f, paint)

        paint.color = YELLOW
        val sq = w * 0.17f
        canvas.drawRect(w * 0.07f, w * 0.52f, w * 0.07f + sq, w * 0.52f + sq, paint)

        paint.color = BLUE
        val baseW = w * 0.24f
        val x = w * 0.80f
        val top = w * 0.80f
        triangle.reset()
        triangle.moveTo(x - baseW / 2f, top + baseW * 0.87f)
        triangle.lineTo(x + baseW / 2f, top + baseW * 0.87f)
        triangle.lineTo(x, top)
        triangle.close()
        canvas.drawPath(triangle, paint)
    }

    private companion object {
        const val RED = 0xFFD9352A.toInt()
        const val YELLOW = 0xFFF2B705.toInt()
        const val BLUE = 0xFF2A5DB8.toInt()
    }
}
