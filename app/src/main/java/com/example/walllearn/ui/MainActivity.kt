package com.example.walllearn.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.walllearn.R
import com.example.walllearn.core.AppPrefs
import com.example.walllearn.core.WallpaperController
import com.example.walllearn.core.WordRepository
import com.example.walllearn.databinding.ActivityMainBinding
import com.example.walllearn.databinding.DialogAddWordBinding
import com.example.walllearn.service.WallpaperUpdateService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableWallLearn()
            } else {
                // Continue anyway: the service still works, it just can't
                // show its "running" notification on Android 13+ without it,
                // which makes Android more likely to eventually kill it.
                enableWallLearn()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggleButton.setOnClickListener { onToggleClicked() }
        binding.nextWordButton.setOnClickListener { onShowNewWordClicked() }
        binding.batteryButton.setOnClickListener { requestBatteryOptimizationExemption() }
        binding.addWordButton.setOnClickListener { showAddWordDialog() }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun onToggleClicked() {
        if (AppPrefs.isServiceEnabled(this)) {
            disableWallLearn()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enableWallLearn()
            }
        }
    }

    private fun enableWallLearn() {
        AppPrefs.setServiceEnabled(this, true)
        WallpaperUpdateService.start(this)
        // Show a fresh word immediately rather than waiting for the next wake-up.
        runInBackground { WallpaperController.applyNextWallpaper(applicationContext) }
        refreshUi()
    }

    private fun disableWallLearn() {
        AppPrefs.setServiceEnabled(this, false)
        WallpaperUpdateService.stop(this)
        refreshUi()
    }

    private fun onShowNewWordClicked() {
        runInBackground {
            WallpaperController.applyNextWallpaper(applicationContext)
            mainHandler.post { refreshUi() }
        }
    }

    private fun showAddWordDialog() {
        val dialogBinding = DialogAddWordBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_word_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_add_word) { _, _ ->
                val word = dialogBinding.wordInput.text?.toString().orEmpty().trim()
                val pos = dialogBinding.posInput.text?.toString().orEmpty().trim()
                val meaning = dialogBinding.meaningInput.text?.toString().orEmpty().trim()
                val example = dialogBinding.exampleInput.text?.toString().orEmpty().trim()

                if (word.isEmpty() || meaning.isEmpty()) {
                    Toast.makeText(this, R.string.add_word_missing_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val added = WordRepository.addWord(this, word, pos, meaning, example)
                Toast.makeText(
                    this,
                    if (added) R.string.add_word_success else R.string.add_word_duplicate,
                    Toast.LENGTH_SHORT
                ).show()
                refreshUi()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun requestBatteryOptimizationExemption() {
        val intent = android.content.Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun refreshUi() {
        val enabled = AppPrefs.isServiceEnabled(this)
        binding.statusText.text = getString(
            if (enabled) R.string.status_enabled else R.string.status_disabled
        )
        binding.toggleButton.text = getString(
            if (enabled) R.string.btn_disable else R.string.btn_enable
        )

        val word = WordRepository.currentWord(this)
        if (word != null) {
            binding.wordText.text = word.word.replaceFirstChar { it.uppercase() }
            binding.posText.text = word.pos
            binding.meaningText.text = word.meaning
            binding.exampleText.text = word.example
            val (position, total) = WordRepository.progress(this)
            binding.progressText.text = getString(R.string.progress_format, position, total)
        }

        binding.batteryButton.visibility = if (!isIgnoringBatteryOptimizations()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Activity.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun runInBackground(block: () -> Unit) {
        Thread(block).start()
    }
}
