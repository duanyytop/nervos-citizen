package dev.gw.dylan.ocrscanner

/**
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/ocrscanner/Mrz.java
 */

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import dev.gw.dylan.camera.CameraFragment
import dev.gw.dylan.utils.Util
import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.Semaphore

class TesseractOCR(
    private val name: String,
    private val fragment: CameraFragment,
    private val assetManager: AssetManager
) {
    private var baseApi: TessBaseAPI? = null
    private var myThread: HandlerThread? = null
    private var myHandler: Handler? = null
    private var cleanHandler: Handler? = null
    private var timeoutHandler: Handler? = null
    var stopping = false
    var isInitialized = false

    // Filled with OCR run times for analysis
    private val times = ArrayList<Long>()

    /**
     * CURRENTLY NOT FUNCTIONAL
     * Timeout Thread, should end OCR detection when timeout occurs
     */
    private val timeout = Runnable {
        Log.e(TAG, "TIMEOUT")
        //            baseApi.stop(); // Does not stop baseApi.getUTF8Text()
    }
    private val scan = Runnable {
        while (!stopping) {
            Log.v(TAG, "Start Scan")
            timeoutHandler!!.postDelayed(timeout, OCR_SCAN_TIMEOUT_MILLIS)
            val time = System.currentTimeMillis()
            val b = fragment.extractBitmap()
            val mrz = ocr(b)
            val timetook = System.currentTimeMillis() - time
            Log.i(TAG, "took " + timetook / 1000f + " sec")
            times.add(timetook)
            if (mrz != null && mrz.valid()) {
                fragment.scanResultFound(mrz)
            }
            timeoutHandler!!.removeCallbacks(timeout)
            try {
                Thread.sleep(INTER_SCAN_DELAY_MILLIS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        Log.e(TAG, "Stopping scan")
    }

    /**
     * Starts OCR scan routine with delay in msec
     * @param delay int how msec before start
     */
    fun startScanner(delay: Int) {
        myHandler!!.postDelayed(scan, delay.toLong())
    }

    /**
     * Starts (enqueues) a stop routine in a new thread, then returns immediately.
     */
    fun stopScanner() {
        val ht = HandlerThread("stopper")
        ht.start()
        cleanHandler = Handler(ht.looper)
        cleanHandler!!.post { cleanup() }
        ht.quitSafely()
    }

    /**
     * Starts a new thread to do OCR and enqueues an initialization task;
     */
    fun initialize(context: Context) {
        myThread = HandlerThread(name)
        myThread!!.start()
        timeoutHandler = Handler()
        myHandler = Handler(myThread!!.looper)
        myHandler!!.post {
            init(context)
            Log.e(TAG, "INIT DONE")
        }
        isInitialized = true
    }

    /**
     * Initializes Tesseract library using traineddata file.
     * Should not be called directly, is public for testing.
     */
    fun init(context: Context) {
        baseApi = TessBaseAPI()
        baseApi!!.setDebug(true)
        var path: String? = null
        path =
            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable()) {
                context.externalCacheDir!!.path
            } else {
                context.cacheDir.path
            }
        path += "/" + Util.FOLDER_PASSPORT_WALLET + "/"
        val trainedDataFile = File(path, FOLDER_TESSERACT_DATA + "/" + trainedData)
        try {
            mDeviceStorageAccessLock.acquire()
            if (!trainedDataFile.exists()) {
                Log.i(TAG, "No existing trained data found, copying from assets..")
                Util.copyAssetsFile(assetManager.open(trainedData), trainedDataFile)
            } else {
                Log.i(TAG, "Existing trained data found")
            }
            mDeviceStorageAccessLock.release()
            baseApi!!.init(
                path,
                trainedData.replace(TRAINED_DATA_EXTENSION, "")
            ) //extract language code from trained data file
        } catch (e: IOException) {
            e.printStackTrace()
            //TODO show error to user, coping failed
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Performs OCR scan to bitmap provided, if tesseract is initialized and not currently stopping.
     * Should not be called directly, is public for testing.
     * @param bitmap Bitmap image to be scanned
     * @return Mrz Object containing result data
     */
    fun ocr(bitmap: Bitmap?): Mrz? {
        if (bitmap == null) return null
        return if (isInitialized && !stopping) {
            val options = BitmapFactory.Options()
            options.inSampleSize = 2
            Log.v(
                TAG,
                "Image dims x: " + bitmap.width + ", y: " + bitmap.height
            )
            baseApi!!.setImage(bitmap)
            baseApi!!.setVariable(
                "tessedit_char_whitelist",
                "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ<"
            )
            val recognizedText = baseApi!!.utF8Text
            Log.v(TAG, "OCR Result: $recognizedText")
            Mrz(recognizedText)
        } else {
            Log.e(
                TAG,
                "Trying ocr() while not initalized or stopping!"
            )
            null
        }
    }

    /**
     * Cleans memory used by Tesseract library and closes OCR thread.
     * After this has been called initialize() needs to be called to restart the thread and init Tesseract
     */
    fun cleanup() {
        if (isInitialized) {
            giveStats()
            stopping = true
            myThread!!.quitSafely()
            myHandler!!.removeCallbacks(scan)
            timeoutHandler!!.removeCallbacks(timeout)
            try {
                myThread!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            myThread = null
            myHandler = null
            baseApi!!.end()
            isInitialized = false
            stopping = false
        }
    }

    /**
     * Prints some statistics about the run time of the OCR scanning
     */
    private fun giveStats() {
        var max: Long = 0
        var curravg: Long = 0
        for (i in times.indices) {
            if (times[i] > max) max = times[i]
            curravg += times[i]
        }
        // prevent divide by zero
        if (times.size > 0) {
            Log.e(
                TAG,
                "Max runtime was " + max / 1000f + " sec and avg was " + curravg / times.size / 1000f + " tot tries: " + times.size
            )
        }
    }

    companion object {
        private const val TAG = "TesseractOCR"
        private const val INTER_SCAN_DELAY_MILLIS: Long = 500
        private const val OCR_SCAN_TIMEOUT_MILLIS: Long = 5000
        private const val trainedData = "ocrb.traineddata"
        private const val FOLDER_TESSERACT_DATA = "tessdata"
        private const val TRAINED_DATA_EXTENSION = ".traineddata"

        /**
         * Lock to ensure only one thread can start copying to device storage.
         */
        private val mDeviceStorageAccessLock = Semaphore(1)
    }
}