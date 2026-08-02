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
package com.buzbuz.smartautoclicker.core.processing.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Save full screen captures of the frames processed by the detection.
 *
 * Captures are saved as PNG files in the application specific external files directory, at
 * Android/data/<application package>/files/capturas. This location requires no extra permission.
 *
 * @param context the Android context.
 */
internal class CaptureSaver(context: Context) {

    /** The directory containing the screen captures. Can be null if the external storage is unavailable. */
    private val capturesDirectory: File? = context.getExternalFilesDir(CAPTURES_DIRECTORY_NAME)

    /**
     * Save the provided full screen frame as a PNG file in the captures directory.
     * This method performs file IO, it should not be called from the main thread.
     *
     * @param screenFrame the bitmap containing the screen display to be saved.
     */
    fun save(screenFrame: Bitmap) {
        val directory = capturesDirectory
        if (directory == null) {
            Log.w(TAG, "Can't save capture, external files directory is unavailable")
            return
        }

        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Can't save capture, unable to create directory ${directory.absolutePath}")
            return
        }

        val captureFile = File(
            directory,
            CAPTURE_FILE_PREFIX + CAPTURE_DATE_FORMAT.format(Date()) + CAPTURE_FILE_EXTENSION
        )

        try {
            FileOutputStream(captureFile).use { stream ->
                screenFrame.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
            }
            Log.d(TAG, "Capture saved at ${captureFile.absolutePath}")
        } catch (ioEx: IOException) {
            Log.w(TAG, "Unable to save capture at ${captureFile.absolutePath}", ioEx)
        }
    }
}

/** The name of the directory containing the screen captures, in the app specific external files directory. */
private const val CAPTURES_DIRECTORY_NAME = "capturas"
/** The prefix of the screen capture file names. */
private const val CAPTURE_FILE_PREFIX = "captura_"
/** The extension of the screen capture files. */
private const val CAPTURE_FILE_EXTENSION = ".png"
/** The quality used for the PNG compression. */
private const val PNG_QUALITY = 100
/** The format of the date in the capture file names. */
private val CAPTURE_DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

/** Tag for logs. */
private const val TAG = "CaptureSaver"
