package dev.gw.dylan.utils

/**
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/utilities/ErrorDialog.java
 */

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object Util {
    const val FOLDER_PASSPORT_WALLET = "CKBPassportWallet"

    /**
     * Copies an InputStream into a File.
     * This is used to copy an InputStream from the assets folder to a file in the FileSystem.
     * Creates nay non-existant parent folders to f.
     *
     * @param is InputStream to be copied.
     * @param f  File to copy data to.
     */
    @Throws(IOException::class)
    fun copyAssetsFile(`is`: InputStream, f: File) {
        if (!f.exists()) {
            if (!f.parentFile.mkdirs()) { //getParent because otherwise it creates a folder with that filename, we just need the dirs
                Log.e("Util", "Cannot create path ")
            }
        }
        val os: OutputStream = FileOutputStream(f, true)
        val bufferSize = 1024 * 1024
        try {
            val bytes = ByteArray(bufferSize)
            while (true) {
                val count = `is`.read(bytes, 0, bufferSize)
                if (count == -1) break
                os.write(bytes, 0, count)
            }
            `is`.close()
            os.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    /**
     * Method for converting a hexString to a byte array.
     * This method is used for signing transaction hashes (which are in hex).
     */
    fun hexStringToByteArray(hStr: String?): ByteArray {
        if (hStr != null) {
            val len = hStr.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hStr[i], 16) shl 4)
                    + Character.digit(hStr[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
        return ByteArray(0)
    }

    /**
     * Method for converting a byte array to a hexString.
     * This method is used for converting a signed 8-byte array back to a hashString in order to
     * display it readable.
     */
    fun byteArrayToHexString(bArray: ByteArray?): String {
        if (bArray != null) {
            val hexArray = "0123456789ABCDEF".toCharArray()
            val hexChars = CharArray(bArray.size * 2)
            for (j in bArray.indices) {
                val v: Int = bArray[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }
        return ""
    }

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        return netInfo != null && netInfo.isConnectedOrConnecting
    }

    fun isNetEnabled(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        return netInfo != null
    }
}