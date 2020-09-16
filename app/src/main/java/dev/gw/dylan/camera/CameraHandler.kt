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
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/camera/CameraHandler.java
 */

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import dev.gw.dylan.R
import dev.gw.dylan.camera.CameraFragmentUtil.CompareSizesByArea
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.TimeUnit


class CameraHandler
/**
 * Constructor, needs a link to a fragment to be able to set parameters in accordence to the
 * size of the devices screen.
 * @param fragment - A fragment which handles the display of the camera preview.
 */(  // The fragment this camera device is associated with
    private val fragment: CameraFragment
) {
    private val TAG = "CameraHandler"
    private val SECONDS_TILL_SCAN_TIMEOUT = 10f

    /**
     * A [Handler] for running tasks in the background.
     * Runs camera preview updater.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * References to and attributes of the CameraDevice
     */
    private var mCameraDevice: CameraDevice? = null
    private var mCameraId: String? = null

    /**
     * Objects needed for the cameraPreview
     */
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var mFlashSupported = false
    private var flashEnabled = false

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            fragment.mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            fragment.mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            fragment.mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            val activity = fragment.activity
            activity?.finish()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
        mBackgroundHandler!!.postDelayed(
            fragment.scanningTakingLongTimeout,
            (SECONDS_TILL_SCAN_TIMEOUT * 1000).toLong()
        )
    }

    /**
     * Stops the background thread and its [Handler].
     */
    fun stopBackgroundThread() {
        mBackgroundHandler!!.removeCallbacks(fragment.scanningTakingLongTimeout)
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = fragment.activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                    ?: continue
                val mPreviewSize = setFragmentPreviewSize(
                    width,
                    height,
                    CameraFragmentUtil.needSwappedDimensions(fragment.activity, characteristics),
                    map
                )
                val orientation = fragment.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    fragment.setAspectRatio(mPreviewSize!!.width, mPreviewSize.height)
                } else {
                    fragment.setAspectRatio(
                        mPreviewSize!!.height, mPreviewSize.width
                    )
                }

                // Check if the flash is supported.
                val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                mFlashSupported = available ?: false
                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // An NPE is thrown when the Camera2API is used but not supported on the device this code runs.
            fragment.showInfoDialog(R.string.ocr_camera_error)
        }
    }

    /**
     * Sets the preview size of the fragment
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     * @param swappedDimensions - boolean indicating if dimensions need to be swapped
     * @param map - Configurationmap of the camera
     * @return mPreviewSize - the previewsize that is set in the fragment
     */
    private fun setFragmentPreviewSize(
        width: Int,
        height: Int,
        swappedDimensions: Boolean,
        map: StreamConfigurationMap
    ): Size? {
        // For still image captures, we use the largest available size.
        val largest = Collections.max(
            Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
            CompareSizesByArea()
        )
        val displaySize = Point()
        fragment.activity.windowManager.defaultDisplay.getSize(displaySize)
        var rotatedPreviewWidth = width
        var rotatedPreviewHeight = height
        var maxPreviewWidth = displaySize.x
        var maxPreviewHeight = displaySize.y
        if (swappedDimensions) {
            rotatedPreviewWidth = height
            rotatedPreviewHeight = width
            maxPreviewWidth = displaySize.y
            maxPreviewHeight = displaySize.x
        }
        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH
        }
        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT
        }
        // Attempting to use too large a preview size could  exceed the camera bus' bandwidth
        // limitation, resulting in gorgeous previews but the storage of garbage capture data.
        val mPreviewSize = CameraFragmentUtil.chooseOptimalSize(
            map.getOutputSizes(
                SurfaceTexture::class.java
            ),
            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
            maxPreviewHeight, largest
        )
        fragment.previewSize = mPreviewSize
        return mPreviewSize
    }

    /**
     * Opens the camera specified by [.mCameraId].
     */
    fun openCamera(width: Int, height: Int) {
        if (ContextCompat.checkSelfPermission(fragment.activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fragment.requestCameraPermission()
            return
        }
        if (ContextCompat.checkSelfPermission(
                fragment.activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Cant start Camera due to no data permissions.")
            return
        }
        setUpCameraOutputs(width, height)
        fragment.configureTransform(width, height)
        val activity = fragment.activity
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!fragment.mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    fun closeCamera() {
        try {
            fragment.mCameraOpenCloseLock.acquire()
            if (null != mCaptureSession) {
                mCaptureSession!!.close()
                mCaptureSession = null
            }
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            fragment.mCameraOpenCloseLock.release()
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = fragment.textureView!!.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(fragment.previewSize!!.width, fragment.previewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)
            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice!!.createCaptureSession(
                Arrays.asList(surface), createCameraCaptureSessionStateCallBack(), null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraCaptureSessionStateCallBack(): CameraCaptureSession.StateCallback {
        return object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                // The camera is already closed
                if (null == mCameraDevice) {
                    return
                }
                // When the session is ready, we start displaying the preview.
                mCaptureSession = cameraCaptureSession
                try {
                    val meteringRectangle = MeteringRectangle(
                        CameraFragmentUtil.getScanRect(
                            fragment.scanSegment
                        ),
                        MeteringRectangle.METERING_WEIGHT_MAX
                    )
                    val meteringRectangleArr = arrayOf(meteringRectangle)

                    // Auto focus should be continuous for camera preview.
                    mPreviewRequestBuilder!!.set(
                        CaptureRequest.CONTROL_AF_REGIONS,
                        meteringRectangleArr
                    )

                    // Finally, we start displaying the camera preview.
                    mPreviewRequest = mPreviewRequestBuilder!!.build()
                    if (!fragment.isStateAlreadySaved) mCaptureSession!!.setRepeatingRequest(
                        mPreviewRequest!!,
                        null, mBackgroundHandler
                    )
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(
                cameraCaptureSession: CameraCaptureSession
            ) {
                fragment.showToast("Failed")
            }
        }
    }

    /**
     * Turn the torch of the device on or off, when it has one.
     */
    fun toggleTorch() {
        try {
            if (!flashEnabled && mFlashSupported) {
                mPreviewRequestBuilder!!.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_TORCH
                )
                mCaptureSession!!.setRepeatingRequest(
                    mPreviewRequestBuilder!!.build(),
                    null,
                    mBackgroundHandler
                )
                Log.e(TAG, "flash enabled")
            } else {
                mPreviewRequestBuilder!!.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF
                )
                mCaptureSession!!.setRepeatingRequest(
                    mPreviewRequestBuilder!!.build(),
                    null,
                    mBackgroundHandler
                )
                Log.e(TAG, "flash disabled")
            }
            flashEnabled = !flashEnabled
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    companion object {
        /**
         * Max preview width and height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080
    }
}