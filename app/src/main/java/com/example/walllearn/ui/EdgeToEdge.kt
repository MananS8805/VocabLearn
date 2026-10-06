package com.example.walllearn.ui

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Lets the scenery background run behind transparent status and navigation
 * bars, and pads [content] so nothing is drawn under them (or the keyboard).
 */
fun ComponentActivity.setUpEdgeToEdge(content: View) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    val baseTop = content.paddingTop
    val baseBottom = content.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
        )
        view.updatePadding(
            left = bars.left,
            top = baseTop + bars.top,
            right = bars.right,
            bottom = baseBottom + bars.bottom
        )
        WindowInsetsCompat.CONSUMED
    }
}
