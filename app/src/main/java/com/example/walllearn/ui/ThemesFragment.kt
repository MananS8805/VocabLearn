package com.example.walllearn.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.core.WallpaperController
import com.example.walllearn.core.WallpaperGenerator
import com.example.walllearn.core.WordRepository
import com.example.walllearn.databinding.FragmentThemesBinding
import com.example.walllearn.databinding.ItemThemeBinding
import com.example.walllearn.model.GreWord
import com.example.walllearn.model.LockTheme
import com.example.walllearn.model.LockThemes
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Picks the lock screen theme. Each card is the real wallpaper renderer run at
 * thumbnail size on the user's current word, so a preview is exactly what the
 * lock screen will show.
 */
class ThemesFragment : Fragment() {

    private var _binding: FragmentThemesBinding? = null
    private val binding get() = _binding!!
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val adapter = ThemeAdapter { choose(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentThemesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.themeGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.themeGrid.adapter = adapter
        binding.themeGrid.itemAnimator = null // previews arrive one by one; don't cross-fade them
        adapter.selectedId = AppPrefs.lockTheme(requireContext()).id
        renderPreviews()

        binding.appThemeToggle.check(
            when (AppPrefs.appTheme(requireContext())) {
                AppPrefs.APP_THEME_LIGHT -> R.id.appThemeLight
                AppPrefs.APP_THEME_BAUHAUS -> R.id.appThemeBauhaus
                else -> R.id.appThemeDark
            }
        )
        binding.appThemeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val chosen = when (checkedId) {
                R.id.appThemeLight -> AppPrefs.APP_THEME_LIGHT
                R.id.appThemeBauhaus -> AppPrefs.APP_THEME_BAUHAUS
                else -> AppPrefs.APP_THEME_DARK
            }
            if (chosen != AppPrefs.appTheme(requireContext())) {
                AppPrefs.setAppTheme(requireContext(), chosen)
                requireActivity().recreate() // restyles every tab
            }
        }
    }

    /** Previews show the current word, which changes while this tab is hidden; refresh on return. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) renderPreviews()
    }

    override fun onDestroyView() {
        main.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun renderPreviews() {
        val appContext = requireContext().applicationContext
        executor.execute {
            val word = WordRepository.currentWord(appContext) ?: SAMPLE_WORD
            val progress = WordRepository.progress(appContext)
            for (theme in LockThemes.all) {
                val bmp = WallpaperGenerator.generate(appContext, word, progress, theme, PREVIEW_W, PREVIEW_H)
                main.post { if (_binding != null) adapter.setPreview(theme.id, bmp) }
            }
        }
    }

    private fun choose(theme: LockTheme) {
        AppPrefs.setLockTheme(requireContext(), theme.id)
        adapter.selectedId = theme.id
        if (AppPrefs.isServiceEnabled(requireContext())) {
            val appContext = requireContext().applicationContext
            executor.execute { WallpaperController.reapplyCurrentWallpaper(appContext) }
            Toast.makeText(requireContext(), getString(R.string.theme_applied, theme.name), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.theme_saved, theme.name), Toast.LENGTH_SHORT).show()
        }
    }

    private class ThemeAdapter(private val onChoose: (LockTheme) -> Unit) :
        RecyclerView.Adapter<ThemeAdapter.Holder>() {

        private val previews = HashMap<String, Bitmap>()
        var selectedId: String = ""
            set(value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }

        fun setPreview(id: String, bmp: Bitmap) {
            previews[id] = bmp
            val i = LockThemes.all.indexOfFirst { it.id == id }
            if (i >= 0) notifyItemChanged(i)
        }

        override fun getItemCount() = LockThemes.all.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val theme = LockThemes.all[position]
            val b = holder.binding
            b.themeName.text = theme.name
            b.themeTagline.text = theme.tagline
            val bmp = previews[theme.id]
            if (bmp != null) b.previewImage.setImageBitmap(bmp) else b.previewImage.setImageDrawable(null)
            val selected = theme.id == selectedId
            b.selectedBadge.visibility = if (selected) View.VISIBLE else View.GONE
            b.previewFrame.setBackgroundResource(if (selected) R.drawable.bg_theme_selected else 0)
            b.root.setOnClickListener { onChoose(theme) }
            b.root.contentDescription = theme.name +
                if (selected) ", " + b.root.context.getString(R.string.cd_selected_theme) else ""
        }

        class Holder(val binding: ItemThemeBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object {
        const val PREVIEW_W = 360
        const val PREVIEW_H = 780
        val SAMPLE_WORD = GreWord(
            "Ephemeral", "adjective", "lasting a very short time",
            "Fame in that industry is remarkably ephemeral."
        )
    }
}
