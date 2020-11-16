package org.nervos.gw

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.nervos.gw.utils.PrefUtil
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ReadPassportActivity : AppCompatActivity() {

    private var progressBar: Group? = null
    private var prefUtil: PrefUtil? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_passport)
        progressBar = findViewById(R.id.read_passport_loading)
        prefUtil = PrefUtil(this)
        findViewById<View>(R.id.read_passport_close).setOnClickListener{
            startActivity(Intent(this, CredentialsActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter != null) {
            val intent = Intent(applicationContext, this.javaClass)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val filter = arrayOf(arrayOf("android.nfc.tech.IsoDep"))
            adapter.enableForegroundDispatch(this, pendingIntent, null, filter)
        }
    }

    override fun onPause() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        adapter?.disableForegroundDispatch(this)
        super.onPause()
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            progressBar?.visibility = View.VISIBLE
            val tag = intent.extras!!.getParcelable<Tag>(NfcAdapter.EXTRA_TAG)
            if (listOf(*tag!!.techList).contains("android.nfc.tech.IsoDep")) {
                val passportNumber = prefUtil?.getPassportNumber()
                val expirationDate = convertDate(prefUtil?.getExpiryDate())
                val birthDate = convertDate(prefUtil?.getBirthDate())
                if (passportNumber != null && passportNumber.isNotEmpty()
                    && expirationDate != null && expirationDate.isNotEmpty()
                    && birthDate != null && birthDate.isNotEmpty()
                ) {
                    val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                    PassportReadTask(this, tag, bacKey, progressBar!!).execute()
                } else {
                    Toast.makeText(this, R.string.nfc_not_supported, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun convertDate(input: String?): String? {
        return if (input == null) {
            null
        } else try {
            SimpleDateFormat("yyMMdd", Locale.US)
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input))
        } catch (e: ParseException) {
            Log.w(MainActivity::class.java.simpleName, e)
            null
        }
    }


}