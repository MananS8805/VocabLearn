package com.example.walllearn.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.core.PhraseSpeaker
import com.example.walllearn.core.SpanishRepository
import com.example.walllearn.core.UserTextRepository
import com.example.walllearn.databinding.FragmentLanguagesBinding
import com.example.walllearn.databinding.ItemUserTextBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

/**
 * Where the learner decides what the lock screen teaches: the English / Spanish / own-text
 * mix (one slider each), the Spanish course level and whether it advances by itself, and
 * the library of imported texts.
 */
class LanguagesFragment : Fragment() {

    private var _binding: FragmentLanguagesBinding? = null
    private val binding get() = _binding!!

    /** True while the controls are being set from saved state, so listeners don't write back. */
    private var syncing = false
    private var speaker: PhraseSpeaker? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLanguagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        val sliders = mapOf(
            AppPrefs.SOURCE_EN to binding.sliderEnglish,
            AppPrefs.SOURCE_ES to binding.sliderSpanish,
            AppPrefs.SOURCE_TX to binding.sliderTexts
        )
        for ((source, slider) in sliders) {
            slider.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
                if (fromUser && !syncing) {
                    AppPrefs.setMixWeight(ctx, source, value.toInt())
                    updateShares()
                }
            }
        }

        binding.spanishModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            AppPrefs.setSpanishAuto(ctx, checkedId == R.id.modeAuto)
            updateSpanish()
        }
        binding.levelToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            SpanishRepository.setLevel(ctx, levelOf(checkedId))
            updateSpanish()
        }
        binding.accentToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            AppPrefs.setSpanishAccent(ctx, accentOf(checkedId))
        }
        binding.sliderPitch.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (fromUser && !syncing) {
                AppPrefs.setSpanishPitch(ctx, value)
                updateVoiceLabels()
            }
        }
        binding.sliderSpeed.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (fromUser && !syncing) {
                AppPrefs.setSpanishRate(ctx, value)
                updateVoiceLabels()
            }
        }
        binding.testVoiceButton.setOnClickListener { testVoice() }
        binding.importTextButton.setOnClickListener {
            startActivity(Intent(ctx, ImportTextActivity::class.java))
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) refresh()
    }

    override fun onDestroyView() {
        speaker?.shutdown()
        speaker = null
        _binding = null
        super.onDestroyView()
    }

    private fun refresh() {
        val ctx = requireContext()
        syncing = true
        binding.sliderEnglish.value = AppPrefs.mixWeight(ctx, AppPrefs.SOURCE_EN).toFloat()
        binding.sliderSpanish.value = AppPrefs.mixWeight(ctx, AppPrefs.SOURCE_ES).toFloat()
        binding.sliderTexts.value = AppPrefs.mixWeight(ctx, AppPrefs.SOURCE_TX).toFloat()
        binding.spanishModeToggle.check(if (SpanishRepository.isAuto(ctx)) R.id.modeAuto else R.id.modeManual)
        binding.levelToggle.check(levelId(SpanishRepository.level(ctx)))
        binding.accentToggle.check(accentId(AppPrefs.spanishAccent(ctx)))
        binding.sliderPitch.value = AppPrefs.spanishPitch(ctx).coerceIn(0.5f, 1.2f)
        binding.sliderSpeed.value = AppPrefs.spanishRate(ctx).coerceIn(0.5f, 1.2f)
        syncing = false
        updateVoiceLabels()
        updateShares()
        updateSpanish()
        renderTexts()
    }

    /** Shows each source's share of the rotation as a percentage of the non-zero weights. */
    private fun updateShares() {
        val ctx = requireContext()
        val weights = AppPrefs.SOURCES.map { AppPrefs.mixWeight(ctx, it) }
        val total = weights.sum()
        val labels = listOf(binding.shareEnglish, binding.shareSpanish, binding.shareTexts)
        weights.forEachIndexed { i, w ->
            labels[i].text = if (w == 0) {
                getString(R.string.mix_off)
            } else {
                getString(R.string.mix_share_format, Math.round(w * 100f / total))
            }
        }
        binding.mixHint.text = getString(
            when {
                total == 0 -> R.string.mix_hint_nothing
                weights[2] > 0 && !UserTextRepository.hasContent(ctx) -> R.string.mix_hint_no_texts
                else -> R.string.mix_hint_default
            }
        )
    }

    private fun updateSpanish() {
        val ctx = requireContext()
        val auto = SpanishRepository.isAuto(ctx)
        val level = SpanishRepository.level(ctx)
        binding.spanishModeHint.setText(if (auto) R.string.spanish_hint_auto else R.string.spanish_hint_manual)
        binding.levelPickerLabel.setText(if (auto) R.string.spanish_pick_auto else R.string.spanish_pick_manual)
        binding.levelName.text = getString(R.string.level_format, level, SpanishRepository.LEVEL_NAMES[level - 1])
        binding.levelDescription.setText(LEVEL_DESCRIPTIONS[level - 1])

        // The level toggle follows the course when auto-advance moves it up in the background.
        if (binding.levelToggle.checkedButtonId != levelId(level)) {
            syncing = true
            binding.levelToggle.check(levelId(level))
            syncing = false
        }
        val (seen, total) = SpanishRepository.levelProgress(ctx)
        binding.levelProgressBar.setProgressCompat(if (total == 0) 0 else seen * 100 / total, false)
        binding.levelProgressText.text = getString(R.string.level_progress_format, seen, total)
    }

    private fun renderTexts() {
        val ctx = requireContext()
        val texts = UserTextRepository.all(ctx)
        binding.textsContainer.removeAllViews()
        binding.textsEmpty.visibility = if (texts.isEmpty()) View.VISIBLE else View.GONE
        for (text in texts) {
            val row = ItemUserTextBinding.inflate(layoutInflater, binding.textsContainer, false)
            row.textTitle.text = text.title.ifBlank { getString(R.string.mix_texts) }
            row.textMeta.text = getString(
                R.string.text_meta_format, text.lines.size,
                getString(if (text.ordered) R.string.text_meta_ordered else R.string.text_meta_shuffled)
            )
            row.textSwitch.isChecked = text.enabled
            row.textSwitch.setOnCheckedChangeListener { _, checked ->
                UserTextRepository.setEnabled(ctx, text.id, checked)
                updateShares()
            }
            row.root.setOnClickListener { startActivity(TextViewerActivity.intent(ctx, text.id)) }
            row.root.setOnLongClickListener {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.delete_text_title)
                    .setMessage(R.string.delete_text_message)
                    .setPositiveButton(R.string.btn_delete) { _, _ ->
                        UserTextRepository.delete(ctx, text.id)
                        refresh()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
                true
            }
            binding.textsContainer.addView(row.root)
        }
    }

    private fun updateVoiceLabels() {
        binding.pitchValue.text = String.format(java.util.Locale.US, "%.1f×", binding.sliderPitch.value)
        binding.speedValue.text = String.format(java.util.Locale.US, "%.2f×", binding.sliderSpeed.value)
    }

    /** Speaks a short Spanish sample with the current accent, pitch and speed. */
    private fun testVoice() {
        val engine = speaker ?: PhraseSpeaker(requireContext()) {
            if (_binding != null) Toast.makeText(requireContext(), R.string.tts_spanish_unavailable, Toast.LENGTH_LONG).show()
        }.also { speaker = it }
        engine.speak(getString(R.string.voice_sample), "es-ES")
    }

    private fun accentId(accent: String) = when (accent) {
        AppPrefs.ACCENT_US -> R.id.accentUs
        AppPrefs.ACCENT_MX -> R.id.accentMx
        AppPrefs.ACCENT_ES -> R.id.accentEs
        else -> R.id.accentAuto
    }

    private fun accentOf(id: Int) = when (id) {
        R.id.accentUs -> AppPrefs.ACCENT_US
        R.id.accentMx -> AppPrefs.ACCENT_MX
        R.id.accentEs -> AppPrefs.ACCENT_ES
        else -> AppPrefs.ACCENT_AUTO
    }

    private fun levelId(level: Int) = LEVEL_IDS[(level - 1).coerceIn(0, LEVEL_IDS.lastIndex)]

    private fun levelOf(id: Int) = LEVEL_IDS.indexOf(id) + 1

    private companion object {
        val LEVEL_IDS = listOf(R.id.level1, R.id.level2, R.id.level3, R.id.level4)
        val LEVEL_DESCRIPTIONS = listOf(
            R.string.level_desc_1, R.string.level_desc_2, R.string.level_desc_3, R.string.level_desc_4
        )
    }
}
