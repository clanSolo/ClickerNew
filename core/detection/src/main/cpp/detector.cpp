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
#include <android/log.h>
#include <android/bitmap.h>
#include <memory>
#include <opencv2/imgproc/imgproc_c.h>

#include "bitmap/androidBitmap.hpp"
#include "detector.hpp"

using namespace cv;
using namespace smartautoclicker;

void Detector::setScreenMetrics(JNIEnv *env, jobject screenImage, double detectionQuality) {
    // Initial the current image mat. When the size of the image change (e.g. rotation), this method should be called
    // to update it.
    fullSizeColorCurrentImage = createColorMatFromARGB8888BitmapData(env, screenImage);

    // Select the scale ratio depending on the screen size.
    // We reduce the size to improve the processing time, but we don't want it to be too small because it will impact
    // the performance of the detection.
    auto maxImageDim = max(fullSizeColorCurrentImage->rows, fullSizeColorCurrentImage->cols);
    if (maxImageDim <= detectionQuality) {
        scaleRatio = 1;
    } else {
        scaleRatio = detectionQuality / maxImageDim;
    }

    // The scale ratio has changed, the prepared conditions can no longer be used
    preparedConditions.clear();
}

void Detector::setScreenImage(JNIEnv *env, jobject screenImage) {
    // Get screen info from the android bitmap format
    fullSizeColorCurrentImage = createColorMatFromARGB8888BitmapData(env, screenImage);

    // Convert to gray for template matching
    cv::Mat fullSizeGrayCurrentImage(fullSizeColorCurrentImage->rows, fullSizeColorCurrentImage->cols, CV_8UC1);
    cv::cvtColor(*fullSizeColorCurrentImage, fullSizeGrayCurrentImage, cv::COLOR_RGBA2GRAY);

    // Scale down the image and store it apart (the cache image is not resized)
    resize(fullSizeGrayCurrentImage, *scaledGrayCurrentImage, Size(), scaleRatio, scaleRatio, INTER_AREA);
}

void Detector::prepareCondition(JNIEnv *env, jlong conditionId, jobject conditionImage) {
    // Get the condition image information from the android bitmap format
    auto fullSizeColorCondition = createColorMatFromARGB8888BitmapData(env, conditionImage);
    if (!fullSizeColorCondition) {
        __android_log_print(ANDROID_LOG_ERROR, "Detector",
                            "Can't prepare condition %lld, invalid condition image", (long long) conditionId);
        return;
    }

    // Compute once and cache all the detection data that does not change during a detection session
    PreparedCondition preparedCondition;
    preparedCondition.fullWidth = fullSizeColorCondition->cols;
    preparedCondition.fullHeight = fullSizeColorCondition->rows;
    preparedCondition.colorMean = cv::mean(*fullSizeColorCondition);
    preparedCondition.scaledGrayCondition = *scaleAndChangeToGray(*fullSizeColorCondition);

    preparedConditions[conditionId] = std::move(preparedCondition);
}

DetectionResult Detector::detectCondition(JNIEnv *env, jlong conditionId, int x, int y, int width, int height, int threshold) {
    return detectCondition(env, conditionId, cv::Rect(x, y, width, height), threshold);
}

DetectionResult Detector::detectCondition(JNIEnv *env, jlong conditionId, cv::Rect fullSizeDetectionRoi, int threshold) {
    // Reset the results cache
    detectionResult.reset();

    // setScreenImage haven't been called first
    if (scaledGrayCurrentImage->empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "Detector",
                            "detectCondition caught an exception");
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Can't detect condition, scaledGrayCurrentImage is empty !");
        return detectionResult;
    }

    // Get the prepared detection data for the condition
    auto preparedConditionIt = preparedConditions.find(conditionId);
    if (preparedConditionIt == preparedConditions.end()) {
        __android_log_print(ANDROID_LOG_ERROR, "Detector",
                            "Condition %lld is not prepared !", (long long) conditionId);
        return detectionResult;
    }
    auto& preparedCondition = preparedConditionIt->second;

    // Clamp the detection area to the image bounds, so a detection area bigger than the screen acts as the whole
    // screen instead of being silently invalid (e.g. previous whole screen conditions converted to a full screen
    // detection area).
    fullSizeDetectionRoi &= cv::Rect(0, 0, fullSizeColorCurrentImage->cols, fullSizeColorCurrentImage->rows);
    if (isRoiOutOfBounds(fullSizeDetectionRoi, *fullSizeColorCurrentImage)) {
        __android_log_print(ANDROID_LOG_ERROR, "Detector",
                            "Full size ROI is invalid, %1d/%2d %3d/%4d in %5d/%6d",
                            fullSizeDetectionRoi.x, fullSizeDetectionRoi.y, fullSizeDetectionRoi.width, fullSizeDetectionRoi.height,
                            fullSizeColorCurrentImage->cols, fullSizeColorCurrentImage->rows);
        return detectionResult;
    }
    auto scaledDetectionRoi = getScaledRoi(fullSizeDetectionRoi.x, fullSizeDetectionRoi.y, fullSizeDetectionRoi.width, fullSizeDetectionRoi.height);
    if (isRoiOutOfBounds(scaledDetectionRoi, *scaledGrayCurrentImage)) {
        __android_log_print(ANDROID_LOG_ERROR, "Detector",
                            "Scaled ROI is invalid, %1d/%2d %3d/%4d in %5d/%6d",
                            scaledDetectionRoi.x, scaledDetectionRoi.y, scaledDetectionRoi.width, scaledDetectionRoi.height,
                            scaledGrayCurrentImage->cols, scaledGrayCurrentImage->rows);
        return detectionResult;
    }

    // Crop the scaled gray current image to only get the detection area
    auto croppedGrayCurrentImage = Mat(*scaledGrayCurrentImage, scaledDetectionRoi);

    // Invalid detection, the condition can't fit in the detection area
    if (croppedGrayCurrentImage.rows < preparedCondition.scaledGrayCondition.rows ||
        croppedGrayCurrentImage.cols < preparedCondition.scaledGrayCondition.cols) {
        __android_log_print(ANDROID_LOG_ERROR, "Detector",
                            "Invalid detection area, condition %lld can't fit in it", (long long) conditionId);
        return detectionResult;
    }

    // Compute the matching results in the scratch buffer of the condition, avoiding an allocation per detection
    matchTemplate(croppedGrayCurrentImage, preparedCondition.scaledGrayCondition, preparedCondition.matchingResults);

    // Until a condition is detected or none fits
    cv::Rect scaledMatchingRoi;
    cv::Rect fullSizeMatchingRoi;
    detectionResult.isDetected = false;
    while (!detectionResult.isDetected) {
        // Find the max value and its position in the result
        locateMinMax(preparedCondition.matchingResults, detectionResult);
        // If the maximum for the whole picture is below the threshold, we will never find.
        if (!isValidMatching(detectionResult, threshold)) break;

        // Calculate the ROI based on the maximum location
        scaledMatchingRoi = getDetectionResultScaledCroppedRoi(preparedCondition.scaledGrayCondition.cols, preparedCondition.scaledGrayCondition.rows);
        fullSizeMatchingRoi = getDetectionResultFullSizeRoi(fullSizeDetectionRoi, preparedCondition.fullWidth, preparedCondition.fullHeight);
        if (isRoiOutOfBounds(scaledMatchingRoi, *scaledGrayCurrentImage) || isRoiOutOfBounds(fullSizeMatchingRoi, *fullSizeColorCurrentImage)) {
            // Roi is out of bounds, invalid match
            markRoiAsInvalidInResults(preparedCondition.matchingResults,scaledMatchingRoi);
            continue;
        }

        // Check if the colors are matching in the candidate area.
        auto fullSizeColorCroppedCurrentImage = Mat(*fullSizeColorCurrentImage, fullSizeMatchingRoi);
        double colorDiff = getColorDiff(fullSizeColorCroppedCurrentImage, preparedCondition.colorMean);
        if (colorDiff < threshold) {
            detectionResult.isDetected = true;
        } else {
            // Colors are invalid, modify the matching result to indicate that.
            markRoiAsInvalidInResults(preparedCondition.matchingResults,scaledMatchingRoi);
        }
    }

    // If the condition is detected, compute the position of the detection and add it to the results.
    if (detectionResult.isDetected) {
        detectionResult.centerX = fullSizeMatchingRoi.x + ((int) (fullSizeMatchingRoi.width / 2));
        detectionResult.centerY = fullSizeMatchingRoi.y + ((int) (fullSizeMatchingRoi.height / 2));
    } else {
        detectionResult.centerX = 0;
        detectionResult.centerY = 0;
    }

    return detectionResult;
}

std::unique_ptr<Mat> Detector::scaleAndChangeToGray(const cv::Mat& fullSizeColored) const {
    // Convert the condition into a gray mat
    cv::Mat fullSizeGrayCondition(fullSizeColored.rows, fullSizeColored.cols, CV_8UC1);
    cv::cvtColor(fullSizeColored, fullSizeGrayCondition, cv::COLOR_RGBA2GRAY);

    // Scale it
    auto scaledGrayCondition = Mat(max((int) (fullSizeGrayCondition.rows * scaleRatio), 1),
                                   max((int) (fullSizeGrayCondition.cols * scaleRatio), 1),
                                   CV_8UC1);
    resize(fullSizeGrayCondition, scaledGrayCondition, Size(), scaleRatio, scaleRatio, INTER_AREA);

    return std::make_unique<cv::Mat>(scaledGrayCondition);
}

void Detector::matchTemplate(const Mat& image, const Mat& condition, Mat& results) {
    // Reuse the scratch buffer between detections, only reallocate it if the dimensions have changed
    int resultsRows = image.rows - condition.rows + 1;
    int resultsCols = image.cols - condition.cols + 1;
    if (results.rows != resultsRows || results.cols != resultsCols || results.type() != CV_32F) {
        results.create(resultsRows, resultsCols, CV_32F);
    }

    cv::matchTemplate(image, condition, results, cv::TM_CCOEFF_NORMED);
}

void Detector::locateMinMax(const cv::Mat& matchingResult, DetectionResult& results) {
    minMaxLoc(matchingResult, &results.minVal, &results.maxVal, &results.minLoc, &results.maxLoc, Mat());
}

bool Detector::isValidMatching(const DetectionResult& results, const int threshold) {
    return results.maxVal > ((double) (100 - threshold) / 100);
}

double Detector::getColorDiff(const cv::Mat& image, const cv::Scalar& conditionColorMean) {
    auto imageColorMean = mean(image);

    double diff = 0;
    for (int i = 0; i < 3; i++) {
        diff += abs(imageColorMean.val[i] - conditionColorMean.val[i]);
    }
    return (diff * 100) / (255 * 3);
}

cv::Rect Detector::getDetectionResultScaledCroppedRoi(int scaledWidth, int scaledHeight) const {
    return {
        detectionResult.maxLoc.x,
        detectionResult.maxLoc.y,
        scaledWidth,
        scaledHeight
    };
}

cv::Rect Detector::getDetectionResultFullSizeRoi(const cv::Rect& fullSizeDetectionRoi, int fullSizeWidth, int fullSizeHeight) const {
    return {
            fullSizeDetectionRoi.x + cvRound(detectionResult.maxLoc.x / scaleRatio),
            fullSizeDetectionRoi.y + cvRound(detectionResult.maxLoc.y / scaleRatio),
            fullSizeWidth,
            fullSizeHeight
    };
}

cv::Rect Detector::getScaledRoi(const int x, const int y, const int width, const int height) const {
    return {
        cvRound(x * scaleRatio),
        cvRound(y * scaleRatio),
        cvRound(width * scaleRatio),
        cvRound(height * scaleRatio)
    };
}

bool Detector::isRoiOutOfBounds(const cv::Rect& roi, const cv::Mat& image) {
    return 0 > roi.x || 0 > roi.width || roi.x + roi.width > image.cols || 0 > roi.y || 0 > roi.height || roi.y + roi.height > image.rows;
}

void Detector::markRoiAsInvalidInResults(const cv::Mat& results, const Rect& roi) {
    cv::rectangle(results, roi, cv::Scalar(0), CV_FILLED);
}
