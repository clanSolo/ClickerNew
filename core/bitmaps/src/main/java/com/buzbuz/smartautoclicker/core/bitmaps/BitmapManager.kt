/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.bitmaps

import android.content.Context
import android.graphics.Bitmap

/** Manages the bitmaps for the click conditions. */
interface BitmapManager {

    companion object {
        /** Singleton preventing multiple instances of the repository at the same time. */
        @Volatile
        private var INSTANCE: BitmapManager? = null

        /**
         * Get the repository singleton, or instantiates it if it wasn't yet.
         *
         * @param context the Android context.
         *
         * @return the repository singleton.
         */
        fun getBitmapManager(context: Context): BitmapManager {
            return INSTANCE ?: synchronized(this) {
                val instance = BitmapManagerImpl(
                    appDataDir = context.filesDir,
                    screenCaptureDir = context.getExternalFilesDir(SCREEN_CAPTURE_DIRECTORY_NAME),
                )
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Save the provided bitmap into the persistent memory.
     * If the bitmap is already saved, does nothing.
     *
     * @param bitmap the bitmap to be saved on the persistent memory.
     *
     * @return the path of the bitmap.
     */
    suspend fun saveBitmap(bitmap: Bitmap, prefix: String = CONDITION_FILE_PREFIX) : String

    /**
     * Save the provided screen capture as a PNG file in the application specific external files directory, at
     * Android/data/<application package>/files/capturas.
     *
     * Unlike the condition bitmaps saved with [saveBitmap], captures are not cached in memory nor deduplicated, and
     * they can't be deleted with [deleteBitmaps].
     *
     * @param bitmap the bitmap of the screen capture to be saved.
     *
     * @return the path of the saved capture file, or null if the capture couldn't be saved.
     */
    suspend fun saveScreenCapture(bitmap: Bitmap) : String?

    /**
     * Load a bitmap.
     * If it was already loaded, returns immediately with the value from the cache. If not, load it from the persistent
     * memory.
     *
     * @param path the path of the bitmap.
     * @param width the width of the bitmap.
     * @param height the height of the bitmap.
     *
     * @return the loaded bitmap, or null if the path is invalid
     */
    suspend fun loadBitmap(path: String, width: Int, height: Int) : Bitmap?

    /**
     * Delete the specified bitmaps from the persistent memory.
     *
     * @param paths the paths of the bitmaps to be deleted.
     */
    fun deleteBitmaps(paths: List<String>)

    /** Release the cache of bitmaps. */
    fun releaseCache()
}

/** The prefix appended to all bitmap file names. */
const val CONDITION_FILE_PREFIX = "Condition_"
/** The prefix appended to all bitmap file names. */
const val TUTORIAL_CONDITION_FILE_PREFIX = "Tutorial_Condition_"
/** The name of the directory containing the screen captures, in the app specific external files directory. */
const val SCREEN_CAPTURE_DIRECTORY_NAME = "capturas"