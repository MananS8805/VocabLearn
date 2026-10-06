package com.example.walllearn.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.databinding.ActivityMainBinding

/**
 * Hosts the four tabs (Today, Words, Essays, Themes) behind a bottom bar. Every tab is
 * created once and shown/hidden, so each keeps its state (search text, scroll position)
 * while you move between them.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedTab = R.id.tab_today
    private var lightLook = false

    private val backToToday = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.bottomNav.selectedItemId = R.id.tab_today
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The theme must be set before the window's views are inflated.
        val look = AppPrefs.appTheme(this)
        when (look) {
            AppPrefs.APP_THEME_LIGHT -> setTheme(R.style.Theme_Glossa_Light)
            AppPrefs.APP_THEME_BAUHAUS -> setTheme(R.style.Theme_Glossa_Bauhaus)
        }
        lightLook = look == AppPrefs.APP_THEME_LIGHT
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bauhausShapes.isVisible = look == AppPrefs.APP_THEME_BAUHAUS
        setUpInsets()
        onBackPressedDispatcher.addCallback(this, backToToday)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                for (tab in TABS) add(R.id.fragmentContainer, tab.create(), tab.tag)
            }.commitNow()
        } else {
            selectedTab = savedInstanceState.getInt(KEY_TAB, R.id.tab_today)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId, animate = true)
            true
        }
        binding.bottomNav.selectedItemId = selectedTab
        showTab(selectedTab, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, selectedTab)
    }

    private fun showTab(id: Int, animate: Boolean) {
        val fm = supportFragmentManager
        val target = TABS.firstOrNull { it.id == id } ?: return
        selectedTab = id
        backToToday.isEnabled = id != R.id.tab_today
        fm.beginTransaction().apply {
            if (animate && Motion.enabled(this@MainActivity)) setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            for (tab in TABS) {
                val f: Fragment = fm.findFragmentByTag(tab.tag) ?: continue
                if (tab.id == target.id) show(f) else hide(f)
            }
        }.commit()
    }

    /**
     * Draws behind the system bars. The tab content is kept clear of the status bar, the bottom
     * bar clear of the gesture/nav bar, and while the keyboard is up the bar is hidden so the
     * content (a search field, say) can use the room.
     */
    private fun setUpInsets() {
        // Light look needs dark status/navigation icons; the others need light ones.
        val barStyle = if (lightLook) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardUp = ime.bottom > bars.bottom
            binding.fragmentContainer.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.navBar.isVisible = !keyboardUp
            binding.navBar.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            binding.content.updatePadding(bottom = if (keyboardUp) ime.bottom else 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    private class Tab(val id: Int, val tag: String, val create: () -> Fragment)

    private companion object {
        const val KEY_TAB = "selected_tab"
        val TABS = listOf(
            Tab(R.id.tab_today, "today") { TodayFragment() },
            Tab(R.id.tab_words, "words") { WordsFragment() },
            Tab(R.id.tab_essays, "essays") { EssaysFragment() },
            Tab(R.id.tab_themes, "themes") { ThemesFragment() }
        )
    }
}
