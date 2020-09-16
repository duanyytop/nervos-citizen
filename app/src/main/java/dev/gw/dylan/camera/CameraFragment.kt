package dev.gw.dylan.camera

/*
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
* https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/camera/CameraFragment.java
*/

import android.Manifest
import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.gw.dylan.R
import dev.gw.dylan.ocrscanner.Mrz
import dev.gw.dylan.ocrscanner.TesseractOCR
import dev.gw.dylan.passport.DocumentData
import dev.gw.dylan.utils.ErrorDialog
import java.util.ArrayList
import java.util.concurrent.Semaphore

class CameraFragment : Fragment() {
    private val tesseractThreads: MutableList<TesseractOCR> = ArrayList()
    private var resultFound = false
    val scanningTakingLongTimeout =
        Runnable { activity.runOnUiThread { overlay!!.setMargins(0, 0, 0, infoText!!.height) } }
    var scanSegment: ImageView? = null
        private set
    private var overlay: Overlay? = null
    private var toggleTorchButton: FloatingActionButton? = null
    private var infoText: TextView? = null
    private var controlPanel: View? = null
    var isStateAlreadySaved = false
    var mPendingShowDialog = false

    // listener for detecting orientation changes
    private var orientationListener: OrientationEventListener? = null

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            orientationListener!!.enable()
            mCameraHandler!!.openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            orientationListener!!.disable()
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    var mCameraOpenCloseLock = Semaphore(1)

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    var textureView: AutoFitTextureView? = null
        private set

    /**
     * The [android.util.Size] of camera preview.
     */
    var previewSize: Size? = null

    /**
     * Handler for the connection with the camera
     */
    private var mCameraHandler: CameraHandler? = null

    /**
     * Handles the setup that can start when the fragment is created.
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orientationListener = object : OrientationEventListener(this.activity) {
            override fun onOrientationChanged(orientation: Int) {
                configureTransform(textureView!!.width, textureView!!.height)
            }
        }
        val threadsToStart = Runtime.getRuntime().availableProcessors() / 2
        createOCRThreads(threadsToStart)
        mCameraHandler = CameraHandler(this)
    }

    /**
     * Create the threads where the OCR will run on.
     * @param amount
     */
    private fun createOCRThreads(amount: Int) {
        for (i in 0 until amount) {
            tesseractThreads.add(TesseractOCR("Thread no $i", this, activity.assets))
        }
        Log.e(TAG, "Running threads: $amount")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    /**
     * Setup the layout and setup the actions associated with the button.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
        scanSegment = view.findViewById<View>(R.id.scan_segment) as ImageView
        overlay = view.findViewById<View>(R.id.overlay) as Overlay
        toggleTorchButton =
            view.findViewById<View>(R.id.toggle_torch_button) as FloatingActionButton
        toggleTorchButton!!.setOnClickListener { mCameraHandler!!.toggleTorch() }
        infoText = view.findViewById<View>(R.id.info_text) as TextView
        controlPanel = view.findViewById(R.id.control)
        val observer = controlPanel!!.viewTreeObserver
        observer.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Set the margins when the view is available.
                overlay!!.setMargins(0, 0, 0, controlPanel!!.height)
                view.findViewById<View>(R.id.control).viewTreeObserver.removeOnGlobalLayoutListener(
                    this
                )
            }
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        mCameraHandler!!.startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView!!.isAvailable) {
            mCameraHandler!!.openCamera(textureView!!.width, textureView!!.height)
        } else {
            textureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
        isStateAlreadySaved = false
        if (mPendingShowDialog) {
            mPendingShowDialog = false
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                showInfoDialog(R.string.ocr_camera_permission_explanation)
            } else if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                showInfoDialog(R.string.storage_permission_explanation)
            }
        } else {
            startOCRThreads()
        }
    }

    /**
     * Displays an information dialog with the given string.
     * @param stringId
     */
    fun showInfoDialog(stringId: Int) {
        ErrorDialog.newInstance(getString(stringId))
            .show(childFragmentManager, FRAGMENT_DIALOG)
    }

    override fun onPause() {
        mCameraHandler!!.closeCamera()
        mCameraHandler!!.stopBackgroundThread()
        stopTesseractThreads()
        isStateAlreadySaved = true
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showInfoDialog(R.string.ocr_camera_permission_explanation)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    private fun requestStoragePermissions() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showInfoDialog(R.string.storage_permission_explanation)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (isStateAlreadySaved) {
                    mPendingShowDialog = true
                } else {
                    showInfoDialog(R.string.ocr_camera_permission_explanation)
                }
            }
        } else if (requestCode == REQUEST_WRITE_PERMISSIONS) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (isStateAlreadySaved) {
                    mPendingShowDialog = true
                } else {
                    showInfoDialog(R.string.storage_permission_explanation)
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Start the threads that will run the OCR scanner.
     */
    private fun startOCRThreads() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestStoragePermissions()
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Cant start OCR due to no camera permissions.")
            return
        }
        var i = 0
        for (ocr in tesseractThreads) {
            ocr.initialize(activity)
            ocr.startScanner(i)
            i += DELAY_BETWEEN_OCR_THREADS_MILLIS
        }
    }

    private fun stopTesseractThreads() {
        for (ocr in tesseractThreads) {
            ocr.stopScanner()
        }
    }

    /**
     * Method for delivering correct MRZ when found. This method returns the MRZ as result data and
     * then exits the activity. This method is synchronized and checks for a boolean to make sure
     * it is only executed once in this fragments lifetime.
     * @param mrz Mrz
     */
    @Synchronized
    fun scanResultFound(mrz: Mrz) {
        if (!resultFound) {
            for (thread in tesseractThreads) {
                thread.stopping = true
            }
            val returnIntent = Intent()
            val data = mrz.prettyData
            returnIntent.putExtra(DocumentData.Identifier, data)
            activity.setResult(Activity.RESULT_OK, returnIntent)
            resultFound = true
            activity.finish()
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = activity
        if (null == textureView || null == previewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0F, 0F, previewSize!!.height.toFloat(), previewSize!!.width
                .toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / previewSize!!.height,
                viewWidth.toFloat() / previewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView!!.setTransform(matrix)
        overlay!!.setRect(CameraFragmentUtil.getScanRect(scanSegment))
    }

    /**
     * Extract a bitmap from the textureview of this fragment.
     * @return
     */
    fun extractBitmap(): Bitmap? {
        return try {
            var bitmap = textureView!!.bitmap
            var rotate = Surface.ROTATION_0
            when (activity.windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_0 -> rotate = 0
                Surface.ROTATION_90 -> rotate = 270
                Surface.ROTATION_180 -> rotate = 180
                Surface.ROTATION_270 -> rotate = 90
            }
            if (rotate != Surface.ROTATION_0) {
                bitmap = CameraFragmentUtil.rotateBitmap(bitmap, rotate)
            }
            val croppedBitmap = CameraFragmentUtil.cropBitmap(bitmap, scanSegment)
            CameraFragmentUtil.getResizedBitmap(
                croppedBitmap,
                croppedBitmap!!.width,
                croppedBitmap.height
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sets the aspect ratio of the textureview
     * @param width
     * @param height
     */
    fun setAspectRatio(width: Int, height: Int) {
        textureView!!.setAspectRatio(width, height)
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    fun showToast(text: String?) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        // tag for the log and the error dialog
        private const val TAG = "CameraFragment"
        private const val FRAGMENT_DIALOG = "dialog"
        private const val DELAY_BETWEEN_OCR_THREADS_MILLIS = 500

        // Conversion from screen rotation to JPEG orientation.
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val REQUEST_WRITE_PERMISSIONS = 3
        fun newInstance(): CameraFragment {
            return CameraFragment()
        }
    }
}