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
package com.buzbuz.smartautoclicker.core.display

import android.graphics.Bitmap
import android.media.Image

import java.nio.ByteBuffer

/**
 * The current frame of the screen display.
 *
 * When the image reader provides its pixels with a layout directly usable by the detection, the frame exposes the
 * buffer of the image ([isDirect] is true) and avoids the full screen bitmap copy. The underlying [Image] remains
 * open until [close] is called, which MUST NOT happen before all accesses to the buffer are finished.
 * When the pixel layout is not directly usable, the frame falls back to the classic recycled bitmap.
 */
class ScreenFrame internal constructor(
    /** The frame pixels as a bitmap, or null when the frame is direct. REUSED between frames, do not cache it. */
    val bitmap: Bitmap?,
    /** The frame pixels buffer, or null when the frame is a bitmap. */
    val buffer: ByteBuffer?,
    /** The width of the frame in pixels, without the row stride padding. */
    val width: Int,
    /** The height of the frame in pixels. */
    val height: Int,
    /** The row stride of the buffer, in bytes. Only relevant for direct frames. */
    val rowStride: Int,
    /** The acquired image backing the buffer, only set for direct frames. */
    private val image: Image?,
) : AutoCloseable {

    companion object {
        /**
         * Build a screen frame from a bitmap, using the fallback detection path.
         *
         * @param bitmap the frame pixels as a bitmap.
         * @return the screen frame providing the bitmap.
         */
        fun fromBitmap(bitmap: Bitmap) = ScreenFrame(
            bitmap = bitmap,
            buffer = null,
            width = bitmap.width,
            height = bitmap.height,
            rowStride = 0,
            image = null,
        )
    }

    /** @return true if the frame gives direct access to its pixels via [buffer], false if it provides a [bitmap]. */
    val isDirect: Boolean = buffer != null

    /** @return the bitmap of the frame. Requires a bitmap frame. */
    fun getRequiredBitmap(): Bitmap = bitmap
        ?: throw IllegalStateException("This screen frame does not provide a bitmap")

    /** @return the pixels buffer of the frame. Requires a direct frame. */
    fun getRequiredBuffer(): ByteBuffer = buffer
        ?: throw IllegalStateException("This screen frame does not provide a buffer")

    /** Closes the underlying image, if any. The buffer of a direct frame must not be used after this call. */
    override fun close() {
        image?.close()
    }
}
