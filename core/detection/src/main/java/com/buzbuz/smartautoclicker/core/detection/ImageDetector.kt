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
package com.buzbuz.smartautoclicker.core.detection

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import androidx.annotation.Keep

import java.nio.ByteBuffer

/**
 * Detects bitmaps within other bitmaps for conditions detection on the screen.
 * All calls should be made on the same thread.
 */
interface ImageDetector : AutoCloseable {

    /**
     * Set the current metrics of the screen.
     * This MUST be called before the first detection, with the first bitmap provided by the screen. All orientation
     * changes must also trigger a call to this method.
     * All following calls to [detectCondition] methods will be verified against the metrics of this bitmap.
     *
     * @param screenBitmap the content of the screen as a bitmap.
     * @param detectionQuality the quality of the detection. The higher the preciser, the lower the faster. Must be
     *                         contained in [DETECTION_QUALITY_MIN] and [DETECTION_QUALITY_MAX].
     */
    fun setScreenMetrics(screenBitmap: Bitmap, detectionQuality: Double)

    /**
     * Set the current metrics of the screen from its dimensions.
     * Same as [setScreenMetrics] with a bitmap, but only requires the size of the screen (e.g. when the detection
     * consumes the screen frames directly from their pixels buffer).
     *
     * @param width the width of the screen in pixels.
     * @param height the height of the screen in pixels.
     * @param detectionQuality the quality of the detection. Must be contained in [DETECTION_QUALITY_MIN] and
     *                         [DETECTION_QUALITY_MAX].
     */
    fun setScreenMetrics(width: Int, height: Int, detectionQuality: Double)

    /**
     * Set the bitmap for the screen.
     * All following calls to [detectCondition] methods will be verified against this bitmap.
     *
     * @param screenBitmap the content of the screen as a bitmap.
     */
    fun setupDetection(screenBitmap: Bitmap)

    /**
     * Set the content of the screen as a pixels buffer.
     * All following calls to [detectCondition] methods will be verified against this content. The buffer MUST
     * remain valid until all detections for this frame are completed.
     *
     * @param screenBuffer the direct buffer with the frame pixels, in RGBA_8888 format.
     * @param width the width of the frame in pixels, without the row stride padding.
     * @param height the height of the frame in pixels.
     * @param rowStride the row stride of the buffer, in bytes.
     */
    fun setupDetection(screenBuffer: ByteBuffer, width: Int, height: Int, rowStride: Int)

    /**
     * Prepare the detection of a condition.
     *
     * The bitmap of a condition never changes during a detection session, so all detection data depending on it is
     * precomputed once here instead of at each [detectCondition] call in order to reduce the processing load.
     * Must be called again for all conditions after each [setScreenMetrics] call, as it invalidates the prepared data.
     *
     * @param conditionId the unique identifier of the condition, will be used by the [detectCondition] calls.
     * @param conditionBitmap the bitmap of the condition.
     */
    fun prepareCondition(conditionId: Long, conditionBitmap: Bitmap)

    /**
     * Detect if the condition is at a specific position in the current screen bitmap.
     * [setupDetection] and [prepareCondition] must have been called first.
     *
     * @param conditionId the unique identifier of the condition, as provided to [prepareCondition].
     * @param position the position on the screen where the condition should be detected.
     * @param threshold the allowed error threshold allowed for the condition.
     *
     * @return the results of the detection.
     */
    fun detectCondition(conditionId: Long, position: Rect, threshold: Int): DetectionResult
}

/** The maximum detection quality for the algorithm. */
const val DETECTION_QUALITY_MAX = 3216L
/** The minimum detection quality for the algorithm. */
const val DETECTION_QUALITY_MIN = 400L

/**
 * The results of a condition detection.
 * @param isDetected true if the condition have been detected. false if not.
 * @param position contains the center of the detected condition in screen coordinates.
 * @param confidenceRate
 */
data class DetectionResult(
    var isDetected: Boolean = false,
    val position: Point = Point(),
    var confidenceRate: Double = 0.0
) {

    /**
     * Set the results of the detection.
     * Used by native code only.
     */
    @Keep
    fun setResults(isDetected: Boolean, centerX: Int, centerY: Int, confidenceRate: Double) {
        this.isDetected = isDetected
        position.set(centerX, centerY)
        this.confidenceRate = confidenceRate
    }
}