package dev.gw.dylan.camera

/**
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/camera/CameraFragmentUtil.java
 */

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.ImageView
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator

object CameraFragmentUtil {
    const val TAG = "CameraFragmentUtil"

    /**
     * Given `choices` of `Size`s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     * class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    fun chooseOptimalSize(
        choices: Array<Size>, textureViewWidth: Int,
        textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
    ): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough: MutableList<Size> = ArrayList()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough: MutableList<Size> = ArrayList()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                if (option.width >= textureViewWidth &&
                    option.height >= textureViewHeight
                ) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return if (bigEnough.size > 0) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else if (notBigEnough.size > 0) {
            Collections.max(notBigEnough, CompareSizesByArea())
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size")
            choices[0]
        }
    }

    /**
     * Rotate the bitmap a given amount of degrees. This is used to get the correct bitmap when
     * the device is in landscape mode.
     * @param bitmap
     * @param degrees
     * @return a rotated bitmap
     */
    fun rotateBitmap(bitmap: Bitmap?, degrees: Int): Bitmap {
        val w = bitmap!!.width
        val h = bitmap.height
        // Setting pre rotate
        val mtx = Matrix()
        mtx.preRotate(degrees.toFloat())
        // Rotating Bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true)
        return Bitmap.createScaledBitmap(rotated, bitmap.width, bitmap.height, true)
    }

    /**
     * Resize a bitmap and return the resized one.
     * @param bm - Initial bitmap
     * @param newWidth
     * @param newHeight
     * @return a resized bitmap
     */
    fun getResizedBitmap(bm: Bitmap?, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm!!.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    /**
     * Crop the bitmap to only the part of the scansegment. The bitmap should only contain the part
     * that displays the MRZ of a travel document.
     * @param bitmap - The bitmap created from the camerapreview
     * @param scanSegment - Scansegment, the segment that should be scanned with OCR
     * @return
     */
    fun cropBitmap(bitmap: Bitmap?, scanSegment: ImageView?): Bitmap {
        val startX = scanSegment!!.x.toInt()
        val startY = scanSegment.y.toInt()
        val width = scanSegment.width
        val length = scanSegment.height
        return Bitmap.createBitmap(bitmap!!, startX, startY, width, length)
    }

    /**
     * Get the scan rectangle.
     * @return The rectangle.
     */
    fun getScanRect(scanSegment: ImageView?): Rect {
        val startX = scanSegment!!.x.toInt()
        val startY = scanSegment.y.toInt()
        val width = scanSegment.width
        val length = scanSegment.height
        return Rect(startX, startY, startX + width, startY + length)
    }

    /**
     * Find out if we need to swap dimensions to get the preview size relative to sensor coordinate.
     * @param activity - The associated activity from which the camera is loaded.
     * @param characteristics - CameraCharacteristics corresponding to the current started cameradevice
     * @return swappedDimensions - A boolean value indicating if the the dimensions need to be swapped.
     */
    fun needSwappedDimensions(activity: Activity, characteristics: CameraCharacteristics): Boolean {
        val displayRotation = activity.windowManager.defaultDisplay.rotation
        val mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_90 -> if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                swappedDimensions = true
            }
            Surface.ROTATION_180, Surface.ROTATION_270 -> if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                swappedDimensions = true
            }
            else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
        }
        return swappedDimensions
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height -
                    rhs.width.toLong() * rhs.height
            )
        }
    }
}