package com.example.walllearn.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.core.PhraseSpeaker
import com.example.walllearn.core.Rotation
import com.example.walllearn.core.WallpaperController
import com.example.walllearn.core.WordRepository
import com.example.walllearn.databinding.DialogAddWordBinding
import com.example.walllearn.databinding.FragmentTodayBinding
import com.example.walllearn.service.WallpaperUpdateService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The home tab: today's word, the lock-screen switch, and adding your own words. */
class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val mainHandler = Handler(Looper.getMainLooper())
    private var typer: ValueAnimator? = null
    private var speaker: PhraseSpeaker? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Continue either way: the service still works without the notification
            // permission on Android 13+, Android is just more likely to stop it eventually.
            enableLockScreen()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.serviceSwitch.setOnCheckedChangeListener { _, _ -> onToggleClicked() }
        binding.nextWordButton.setOnClickListener { onShowNewWordClicked() }
        binding.batteryButton.setOnClickListener { requestBatteryOptimizationExemption() }
        binding.addWordButton.setOnClickListener { showAddWordDialog() }
        refreshUi(animateWord = false)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refreshUi(animateWord = false)
    }

    override fun onDestroyView() {
        typer?.cancel()
        typer = null
        mainHandler.removeCallbacksAndMessages(null)
        speaker?.shutdown()
        speaker = null
        _binding = null
        super.onDestroyView()
    }

    private fun onToggleClicked() {
        val ctx = requireContext()
        if (AppPrefs.isServiceEnabled(ctx)) {
            disableLockScreen()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableLockScreen()
        }
    }

    private fun enableLockScreen() {
        val ctx = requireContext().applicationContext
        AppPrefs.setServiceEnabled(ctx, true)
        WallpaperUpdateService.start(ctx)
        // Show a fresh word immediately rather than waiting for the next wake-up.
        runInBackground { WallpaperController.applyNextWallpaper(ctx) }
        if (_binding != null) refreshUi(animateWord = false)
    }

    private fun disableLockScreen() {
        val ctx = requireContext().applicationContext
        AppPrefs.setServiceEnabled(ctx, false)
        WallpaperUpdateService.stop(ctx)
        refreshUi(animateWord = false)
    }

    private fun onShowNewWordClicked() {
        val ctx = requireContext().applicationContext
        runInBackground {
            WallpaperController.applyNextWallpaper(ctx)
            mainHandler.post { if (_binding != null) refreshUi(animateWord = true) }
        }
    }

    private fun showAddWordDialog() {
        val ctx = requireContext()
        val dialogBinding = DialogAddWordBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.add_word_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_add_word) { _, _ ->
                val word = dialogBinding.wordInput.text?.toString().orEmpty().trim()
                val pos = dialogBinding.posInput.text?.toString().orEmpty().trim()
                val meaning = dialogBinding.meaningInput.text?.toString().orEmpty().trim()
                val example = dialogBinding.exampleInput.text?.toString().orEmpty().trim()

                if (word.isEmpty() || meaning.isEmpty()) {
                    Toast.makeText(ctx, R.string.add_word_missing_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val added = WordRepository.addWord(ctx, word, pos, meaning, example)
                Toast.makeText(
                    ctx,
                    if (added) R.string.add_word_success else R.string.add_word_duplicate,
                    Toast.LENGTH_SHORT
                ).show()
                refreshUi(animateWord = false)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun speak(text: String, languageTag: String) {
        val engine = speaker ?: PhraseSpeaker(requireContext()) {
            if (_binding != null) Toast.makeText(requireContext(), R.string.tts_spanish_unavailable, Toast.LENGTH_LONG).show()
        }.also { speaker = it }
        engine.speak(text, languageTag)
    }

    private fun requestBatteryOptimizationExemption() {
        val ctx = requireContext()
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        )
    }

    private fun refreshUi(animateWord: Boolean) {
        val ctx = requireContext()
        val enabled = AppPrefs.isServiceEnabled(ctx)
        binding.statusText.text = getString(
            if (enabled) R.string.status_enabled else R.string.status_disabled
        )
        // Detach the listener so syncing the switch to saved state doesn't toggle the service.
        binding.serviceSwitch.setOnCheckedChangeListener(null)
        binding.serviceSwitch.isChecked = enabled
        binding.serviceSwitch.setOnCheckedChangeListener { _, _ -> onToggleClicked() }

        val word = Rotation.current(ctx)
        if (word != null) {
            showWord(word.word.replaceFirstChar { it.uppercase() }, animateWord)
            binding.posText.text = word.pos
            binding.posText.visibility = if (word.pos.isBlank()) View.GONE else View.VISIBLE
            binding.meaningText.text = word.meaning
            // Spanish and imported cards carry a plain teaching note; only English examples are quoted.
            binding.exampleText.text =
                if (word.speakLang != null) word.example else getString(R.string.sense_example_format, word.example)
            binding.exampleText.visibility = if (word.example.isBlank()) View.GONE else View.VISIBLE
            val (position, total) = WordRepository.progress(ctx)
            binding.progressText.text = word.footer ?: getString(R.string.progress_format, position, total)

            val tag = word.speakLang
            binding.speakButton.visibility = if (tag != null) View.VISIBLE else View.GONE
            binding.speakButton.setOnClickListener {
                if (tag != null) speak(word.word, tag)
            }
        }

        binding.batteryButton.visibility = if (!isIgnoringBatteryOptimizations()) View.VISIBLE else View.GONE
    }

    /** Sets the headword; with [animate] it types in letter by letter (unless animations are off). */
    private fun showWord(text: String, animate: Boolean) {
        typer?.cancel()
        typer = null
        val view = binding.wordText
        if (!animate || !Motion.enabled(requireContext()) || text.length < 2) {
            view.text = text
            return
        }
        typer = ValueAnimator.ofInt(1, text.length).apply {
            duration = (text.length * TYPE_MS_PER_LETTER).coerceAtMost(TYPE_MAX_MS)
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { _binding?.wordText?.text = text.substring(0, it.animatedValue as Int) }
            start()
        }
        view.text = text.substring(0, 1)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val ctx = requireContext()
        val pm = ctx.getSystemService(Activity.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    private fun runInBackground(block: () -> Unit) {
        Thread(block).start()
    }

    private companion object {
        const val TYPE_MS_PER_LETTER = 70L
        const val TYPE_MAX_MS = 900L
    }
}
