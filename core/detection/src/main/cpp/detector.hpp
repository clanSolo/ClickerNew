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

#include <jni.h>
#include <opencv2/imgproc/imgproc.hpp>
#include <unordered_map>

#include "types/detectionResult.hpp"

namespace smartautoclicker {

    /**
     * Precomputed detection data for a condition.
     * Conditions bitmaps never change during a detection session, so their gray scaled version, color mean and
     * dimensions are computed only once in Detector::prepareCondition instead of at every detection.
     */
    struct PreparedCondition {
        /** Gray scaled version of the condition bitmap, used by the template matching. */
        cv::Mat scaledGrayCondition;
        /** Mean of the color of the condition bitmap, used by the color verification. */
        cv::Scalar colorMean;
        /** Width of the full size condition bitmap. */
        int fullWidth = 0;
        /** Height of the full size condition bitmap. */
        int fullHeight = 0;
        /** Scratch buffer for the template matching results, reused between detections to avoid allocations. */
        cv::Mat matchingResults;
    };

    class Detector {

    private:
        double scaleRatio = 1;

        std::unique_ptr<cv::Mat> fullSizeColorCurrentImage = nullptr;
        std::unique_ptr<cv::Mat> scaledGrayCurrentImage = std::make_unique<cv::Mat>();

        /** The detection data for the conditions, prepared once per session and keyed by condition id. */
        std::unordered_map<jlong, PreparedCondition> preparedConditions;

        DetectionResult detectionResult;

        std::unique_ptr<cv::Mat> scaleAndChangeToGray(const cv::Mat &fullSizeColored) const;

        static void matchTemplate(const cv::Mat& image, const cv::Mat& condition, cv::Mat& results);
        static void locateMinMax(const cv::Mat& matchingResult, DetectionResult& results);
        static bool isValidMatching(const DetectionResult& results, const int threshold);
        static double getColorDiff(const cv::Mat& image, const cv::Scalar& conditionColorMean);

        cv::Rect getDetectionResultScaledCroppedRoi(int scaledWidth, int scaledHeight) const;
        cv::Rect getDetectionResultFullSizeRoi(const cv::Rect& detectionRoi, int fullSizeWidth, int fullSizeHeight) const;
        cv::Rect getScaledRoi(const int x, const int y, const int width, const int height) const;
        static bool isRoiOutOfBounds(const cv::Rect &roi, const cv::Mat &image);
        static void markRoiAsInvalidInResults(const cv::Mat& results, const cv::Rect& roi);

        DetectionResult detectCondition(JNIEnv *env, jlong conditionId, cv::Rect fullSizeDetectionRoi, int threshold);

        /** Update the scaled gray version of the current full size color image. */
        void updateScaledGrayImage();

    public:

        Detector() = default;

        void setScreenMetrics(JNIEnv *env, jobject screenImage, double detectionQuality);

        void setScreenMetrics(int width, int height, double detectionQuality);

        void setScreenImage(JNIEnv *env, jobject screenImage);

        void setScreenImagePixels(void* pixels, int width, int height, std::size_t rowStride);

        void prepareCondition(JNIEnv *env, jlong conditionId, jobject conditionImage);

        DetectionResult detectCondition(JNIEnv *env, jlong conditionId, int x, int y, int width, int height, int threshold);
    };
}

