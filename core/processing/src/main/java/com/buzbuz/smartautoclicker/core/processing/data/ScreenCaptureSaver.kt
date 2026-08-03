/*
 * Copyright (C) 2026 Kevin Buzeau
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
import android.graphics.Canvas
import android.os.Environment
import android.util.Log

import com.buzbuz.smartautoclicker.core.display.ScreenFrame
import com.buzbuz.smartautoclicker.core.domain.model.event.Event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the screen frames that triggered the events as PNG capture files.
 *
 * The captures are written in the application specific external pictures directory, allowing them to be
 * browsed with any file explorer without root access nor extra permissions:
 * <sdk>/Android/data/<package>/files/Pictures/Captures
 * There is no limit nor throttling: one capture is saved for each trigger of an event with
 * [Event.takeCaptures] enabled.
 */
internal class ScreenCaptureSaver {

    /** Scope for the asynchronous file writes. Kept alive between detection sessions. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The directory where the captures are written, or null if the saver is not started. */
    private var capturesDir: File? = null

    /** Select the destination directory for the captures. */
    fun start(context: Context) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.let { File(it, CAPTURES_DIRECTORY_NAME) }
            ?: File(context.filesDir, CAPTURES_DIRECTORY_NAME)

        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Cannot create the captures directory ${dir.absolutePath}")
            capturesDir = null
            return
        }

        Log.i(TAG, "Triggered events captures will be saved into ${dir.absolutePath}")
        capturesDir = dir
    }

    /** Stop the saver until the next call to [start]. */
    fun stop() {
        capturesDir = null
    }

    /**
     * Save the screen frame that triggered the provided [event] as a PNG capture.
     *
     * The frame is closed and its pixels are recycled as soon as the processing of the image is completed, so its
     * content is copied synchronously here, and the PNG encoding and file write are executed asynchronously.
     *
     * @param event the event that has been triggered.
     * @param screenFrame the frame the event has been triggered on.
     */
    fun saveTriggerCapture(event: Event, screenFrame: ScreenFrame) {
        val dir = capturesDir ?: return

        val frameCopy = screenFrame.copyPixels() ?: return
        val file = File(dir, buildCaptureFileName(event))

        writeScope.launch {
            val isSaved = withContext(Dispatchers.IO) {
                try {
                    file.outputStream().use { stream ->
                        frameCopy.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
                    }
                } catch (ioEx: IOException) {
                    Log.w(TAG, "Cannot write capture ${file.name}", ioEx)
                    false
                }
            }
            frameCopy.recycle()

            if (isSaved) Log.d(TAG, "Capture saved: ${file.absolutePath}")
        }
    }

    /** @return an exclusive copy of the frame pixels at the exact frame size, or null if the frame can't be copied. */
    private fun ScreenFrame.copyPixels(): Bitmap? =
        if (isDirect) copyDirectFrame(getRequiredBuffer(), width, height, rowStride)
        else getRequiredBitmap().copyExclusive()

    /**
     * Copy a direct frame into a bitmap.
     * The padding added at the end of each row by the row stride is skipped, so no vertical garbage band appears in
     * the resulting capture.
     */
    private fun copyDirectFrame(buffer: ByteBuffer, width: Int, height: Int, rowStrideBytes: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val bytesPerRow = width * BYTES_PER_PIXEL
        if (rowStrideBytes < bytesPerRow) {
            Log.w(TAG, "Invalid row stride $rowStrideBytes for a $width pixels wide frame")
            return null
        }
        if (buffer.capacity() < rowStrideBytes * (height - 1) + bytesPerRow) {
            Log.w(TAG, "Direct frame buffer is too small: ${buffer.capacity()} bytes")
            return null
        }

        val copy = buffer.duplicate()
        copy.position(0)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (rowStrideBytes == bytesPerRow) {
            bitmap.copyPixelsFromBuffer(copy)
        } else {
            val cleanBuffer = ByteBuffer.allocateDirect(bytesPerRow * height)
            val row = ByteArray(bytesPerRow)
            for (i in 0 until height) {
                copy.get(row)
                cleanBuffer.put(row)
                // Skip the padded bytes at the end of the row, if any
                copy.position(copy.position() + rowStrideBytes - bytesPerRow)
            }
            cleanBuffer.position(0)
            bitmap.copyPixelsFromBuffer(cleanBuffer)
        }

        return bitmap
    }

    /** @return an exclusive copy of this bitmap, as the frame bitmaps are recycled between frames. */
    private fun Bitmap.copyExclusive(): Bitmap =
        copy(config ?: Bitmap.Config.ARGB_8888, false)
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { copy ->
                Canvas(copy).drawBitmap(this, 0f, 0f, null)
            }

    /** @return the name of the capture file for this event trigger. */
    private fun buildCaptureFileName(event: Event): String {
        val eventName = event.name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(MAX_EVENT_NAME_LENGTH)
        val timestamp = SimpleDateFormat(CAPTURE_DATE_FORMAT, Locale.US).format(Date())
        return "${CAPTURE_FILE_PREFIX}${eventName}_${timestamp}.png"
    }
}

/** Tag for logs. */
private const val TAG = "ScreenCaptureSaver"
/** Name of the directory containing the captures, in the app pictures directory. */
private const val CAPTURES_DIRECTORY_NAME = "Captures"
/** Prefix of all capture file names. */
private const val CAPTURE_FILE_PREFIX = "Capture_"
/** Date format used in the capture file names, ensuring uniqueness and ordering. */
private const val CAPTURE_DATE_FORMAT = "yyyyMMdd_HHmmss_SSS"
/** Maximum length of the event name part of a capture file name. */
private const val MAX_EVENT_NAME_LENGTH = 40
/** PNG is lossless, the quality value is ignored, but must be provided. */
private const val PNG_QUALITY = 100
/** Size of a pixel in a RGBA_8888 direct frame buffer. */
private const val BYTES_PER_PIXEL = 4
