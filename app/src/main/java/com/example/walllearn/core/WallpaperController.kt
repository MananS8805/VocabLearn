package com.example.walllearn.core

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import com.example.walllearn.model.GreWord
import java.io.IOException

/**
 * The single place that ties word rotation, bitmap rendering, and
 * actually applying the result to the system wallpaper. Both the
 * always-on [com.example.walllearn.service.WallpaperUpdateService] and the
 * "show a new word now" button in the UI go through this so behavior
 * stays identical either way.
 */
object WallpaperController {

    private const val TAG = "WallpaperController"

    /**
     * Advances the word rotation, renders the new wallpaper, and applies
     * it to the lock screen only (the home screen is left untouched).
     * Returns the word that was applied, or null if applying the
     * wallpaper failed (for example, because the SET_WALLPAPER permission
     * was revoked).
     */
    fun applyNextWallpaper(context: Context): GreWord? {
        val word = WordRepository.advance(context)
        return if (applyWord(context, word)) word else null
    }

    /** Re-renders and re-applies whatever word is currently active, without advancing. */
    fun reapplyCurrentWallpaper(context: Context): GreWord? {
        val word = WordRepository.currentWord(context) ?: WordRepository.advance(context)
        return if (applyWord(context, word)) word else null
    }

    private fun applyWord(context: Context, word: GreWord): Boolean {
        return try {
            val progress = WordRepository.progress(context)
            val bitmap = WallpaperGenerator.generate(context, word, progress)
            val wallpaperManager = WallpaperManager.getInstance(context.applicationContext)
            wallpaperManager.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_LOCK
            )
            bitmap.recycle()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to apply wallpaper", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to set wallpaper", e)
            false
        }
    }
}
