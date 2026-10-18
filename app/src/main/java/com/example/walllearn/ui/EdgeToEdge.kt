package com.example.walllearn.ui

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs

/**
 * Lets the scenery background run behind transparent status and navigation
 * bars, and pads [content] so nothing is drawn under them (or the keyboard).
 * Pass [light] for the light app look, which needs dark status and navigation icons.
 */
fun ComponentActivity.setUpEdgeToEdge(content: View, light: Boolean = false) {
    val barStyle = if (light) {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    } else {
        SystemBarStyle.dark(Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
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

/**
 * Applies the app look chosen in the Themes tab (dark, light or Bauhaus) to a secondary
 * screen. Must run before super.onCreate. Returns true when the light look is active.
 */
fun AppCompatActivity.applyAppLook(): Boolean {
    val look = AppPrefs.appTheme(this)
    when (look) {
        AppPrefs.APP_THEME_LIGHT -> setTheme(R.style.Theme_Glossa_Light)
        AppPrefs.APP_THEME_BAUHAUS -> setTheme(R.style.Theme_Glossa_Bauhaus)
    }
    return look == AppPrefs.APP_THEME_LIGHT
}
