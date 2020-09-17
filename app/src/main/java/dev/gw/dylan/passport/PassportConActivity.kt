package dev.gw.dylan.passport

/**
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/passportconnection/PassportConActivity.java
 */

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.snackbar.Snackbar
import dev.gw.dylan.R
import dev.gw.dylan.passport.PassportCrypto.publicKeyToPemFormat
import dev.gw.dylan.passport.PassportCrypto.signTxHash
import dev.gw.dylan.passport.PassportCrypto.verifyTxHashSignature
import dev.gw.dylan.utils.Util
import dev.gw.dylan.wallet.MainActivity.Companion.GET_DOC_INFO
import org.jmrtd.PassportService
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.Locale

class PassportConActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Passport"

        /**
         * Setup the recognition of nfc tags when the activity is opened (foreground)
         *
         * @param activity The corresponding [Activity] requesting the foreground dispatch.
         * @param adapter The [NfcAdapter] used for the foreground dispatch.
         */
        fun setupForegroundDispatch(activity: Activity, adapter: NfcAdapter?) {
            val intent = Intent(activity.applicationContext, activity.javaClass)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pendingIntent = PendingIntent.getActivity(activity.applicationContext, 0, intent, 0)
            val filters = arrayOfNulls<IntentFilter>(1)
            val techList = arrayOf<Array<String>>()

            // Filter for nfc tag discovery
            filters[0] = IntentFilter()
            filters[0]!!.addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
            filters[0]!!.addCategory(Intent.CATEGORY_DEFAULT)
            adapter!!.enableForegroundDispatch(activity, pendingIntent, filters, techList)
        }

        /**
         * @param activity The corresponding [] requesting to stop the foreground dispatch.
         * @param adapter The [NfcAdapter] used for the foreground dispatch.
         */
        fun stopForegroundDispatch(activity: Activity?, adapter: NfcAdapter?) {
            adapter!!.disableForegroundDispatch(activity)
        }

        init {
            Security.insertProviderAt(BouncyCastleProvider(), 0)
        }
    }

    // Adapter for NFC connection
    private var mNfcAdapter: NfcAdapter? = null
    private var documentData: DocumentData? = null
    private var progressView: ImageView? = null
    private var thisActivity: PassportConActivity? = null

    /**
     * This activity usually be loaded from the starting screen of the app.
     * This method handles the start-up of the activity, it does not need to call any other methods
     * since the activity onNewIntent() calls the intentHandler when a NFC chip is detected.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        documentData = extras!![DocumentData.Companion.Identifier] as DocumentData?
        thisActivity = this
        setContentView(R.layout.activity_passport_con)
        val appBar = findViewById<View>(R.id.app_bar) as Toolbar
        setSupportActionBar(appBar)
        val notice = findViewById<View>(R.id.notice) as TextView
        progressView = findViewById<View>(R.id.progress_view) as ImageView
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        checkNFCStatus()
        notice.setText(R.string.nfc_enabled)
    }

    /**
     * Some methods to ensure that when the activity is opened the ID is read.
     * When the activity is opened any nfc device held against the phone will cause, handleIntent to
     * be called.
     */
    override fun onResume() {
        super.onResume()
        // It's important, that the activity is in the foreground (resumed). Otherwise an IllegalStateException is thrown.
        setupForegroundDispatch(this, mNfcAdapter)
        checkNFCStatus()
    }

    override fun onPause() {
        // Call this before super.onPause, otherwise an IllegalArgumentException is thrown as well.
        stopForegroundDispatch(this, mNfcAdapter)
        super.onPause()
    }

    /**
     * This method gets called, when a new Intent gets associated with the current activity instance.
     * Instead of creating a new activity, onNewIntent will be called. For more information have a look
     * at the documentation.
     *
     * In our case this method gets called, when the user attaches a Tag to the device.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Handle the intent following from a NFC detection.
     *
     */
    private fun handleIntent(intent: Intent) {
        progressView!!.setImageResource(R.drawable.nfc_icon_1)

        // if nfc tag holds no data, return
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return

        // Open a connection with the ID, return a PassportService object which holds the open connection
        val pcon = PassportConnection()
        val ps: PassportService?
        ps = try {
            pcon.openConnection(tag, documentData)
        } catch (e: Exception) {
            handleConnectionFailed(e)
            null
        }
        if (ps != null) {
            try {

                // Get public key from dg15
                val pubKey = pcon.getAAPublicKey(ps)
                Log.d(TAG, "Public key with pem format: ${publicKeyToPemFormat(pubKey)}")

                // Get person information from dg1
                val person = pcon.getPerson(ps)

                val origin = Util.hexStringToByteArray("b30d0d9fa0c8bbdf27a746ef51d83114fbce7054d8818ececb84ec129e350725")
                val signature = signTxHash(origin, pcon)
                Log.d(TAG, "Tx Hash signature: ${Util.byteArrayToHexString(signature)}")

                val result = verifyTxHashSignature(pubKey, origin, signature)
                Log.d(TAG, "Verify signature: $result")

                Toast.makeText(this," name: " + person.firstName, Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                handleConnectionFailed(ex)
            } finally {
                try {
                    ps.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * When the connection fails, the exception gives more information about the error
     * Display error messages to the user accordingly.
     * @param e - The exception that was raised when the passportconnectoin failed
     */
    private fun handleConnectionFailed(e: Exception) {
        Log.d(TAG, e.toString())
        if (e.toString().toLowerCase(Locale.ROOT).contains("authentication failed")) {
            progressView!!.setImageResource(R.drawable.nfc_icon_empty)
        } else if (e.toString().toLowerCase(Locale.ROOT).contains("tag was lost")) {
            Toast.makeText(this, getString(R.string.NFC_error), Toast.LENGTH_LONG).show()
            progressView!!.setImageResource(R.drawable.nfc_icon_empty)
        } else {
            Toast.makeText(this, getString(R.string.general_error), Toast.LENGTH_LONG).show()
            progressView!!.setImageResource(R.drawable.nfc_icon_empty)
        }
    }

    /**
     * Check if NFC is enabled and display error message when it is not.
     * This method should be called each time the activity is resumed, because people could change their
     * settings while the app is open.
     */
    fun checkNFCStatus() {
        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, R.string.nfc_not_supported_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // Display a notice that NFC is disabled and provide user with option to turn on NFC
        if (!mNfcAdapter!!.isEnabled) {
            // Add listener for action in snackbar
            val nfcSnackbarListener = View.OnClickListener {
                thisActivity!!.startActivity(
                    Intent(
                        Settings.ACTION_NFC_SETTINGS
                    )
                )
            }
            val nfcDisabledSnackbar = Snackbar.make(
                findViewById(R.id.coordinator_layout),
                R.string.nfc_disabled_error_snackbar, Snackbar.LENGTH_INDEFINITE
            )
            nfcDisabledSnackbar.setAction(
                R.string.nfc_disabled_snackbar_action,
                nfcSnackbarListener
            )
            nfcDisabledSnackbar.show()
        }
    }

    /**
     * Update the documentdata in this activity and in the main activity.
     * @param requestCode requestCode
     * @param resultCode resultCode
     * @param data The data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GET_DOC_INFO && resultCode == RESULT_OK) {
            documentData = data!!.extras!![DocumentData.Companion.Identifier] as DocumentData?
        }
    }
}