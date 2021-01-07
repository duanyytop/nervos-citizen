package org.nervos.gw.passport

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.nervos.gw.CredentialsActivity
import org.nervos.gw.R
import org.nervos.gw.utils.DateUtils
import org.nervos.gw.utils.PassportPref

class ReadPassportActivity : AppCompatActivity() {

    private var progressBar: Group? = null
    private var passportPref: PassportPref? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_passport)
        progressBar = findViewById(R.id.read_passport_loading)
        passportPref = PassportPref(this)
        findViewById<View>(R.id.read_passport_close).setOnClickListener{
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
                val passportNumber = passportPref?.getPassportNumber()
                val expirationDate = DateUtils.convertDate(passportPref?.getExpiryDate())
                val birthDate = DateUtils.convertDate(passportPref?.getBirthDate())
                if (passportNumber != null && passportNumber.isNotEmpty()
                    && expirationDate != null && expirationDate.isNotEmpty()
                    && birthDate != null && birthDate.isNotEmpty()
                ) {
                    val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                    PassportReadTask(this, tag, bacKey, object : PassportCallback {
                        override fun handle(result: String?, error: String?) {
                            progressBar?.visibility = View.GONE
                            if (error != null) {
                                Toast.makeText(this@ReadPassportActivity, error, Toast.LENGTH_LONG).show()
                                return
                            }
                            startActivity(Intent(this@ReadPassportActivity, CredentialsActivity::class.java))
                            finish()
                        }
                    }).execute()
                } else {
                    Toast.makeText(this, R.string.passport_info_error, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, R.string.nfc_not_supported, Toast.LENGTH_LONG).show()
            }
        }
    }




}