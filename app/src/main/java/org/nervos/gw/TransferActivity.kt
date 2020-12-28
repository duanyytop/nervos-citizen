package org.nervos.gw

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.nervos.ckb.service.RpcCallback
import org.nervos.ckb.type.Witness
import org.nervos.ckb.type.transaction.Transaction
import org.nervos.gw.passport.PassportCallback
import org.nervos.gw.passport.PassportSignTask
import org.nervos.gw.utils.CKB_EXPLORER_TX_URL
import org.nervos.gw.utils.DateUtils
import org.nervos.gw.utils.PassportPref
import org.nervos.gw.utils.TxUtils

class TransferActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TX = "extra_tx"
        const val EXTRA_PUB_KEY = "extra_pub_key"
    }

    private var readPassportProgress: ProgressBar? = null
    private var transactionHashView: TextView? = null
    private var viewOnExplorer: Button? = null
    private var finishButton: Button? = null
    private var tx: Transaction? = null
    private var publicKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer)
        initView()

        val txStr = intent.getStringExtra(EXTRA_TX)
        tx = Gson().fromJson(txStr, object : TypeToken<Transaction>() {}.type)
        publicKey = intent.getStringExtra(EXTRA_PUB_KEY)
    }

    private fun initView() {
        val toolbar = findViewById<Toolbar>(R.id.transfer_toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener{
            startActivity(Intent(this, CredentialDetailActivity::class.java))
            finish()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        readPassportProgress = findViewById(R.id.transfer_read_progress)
        transactionHashView = findViewById(R.id.transaction_hash)
        viewOnExplorer = findViewById(R.id.action_goto_explorer)
        finishButton = findViewById(R.id.action_finish)

        finishButton?.setOnClickListener {
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
            readPassportProgress?.visibility = View.VISIBLE
            val tag = intent.extras!!.getParcelable<Tag>(NfcAdapter.EXTRA_TAG)
            if (listOf(*tag!!.techList).contains("android.nfc.tech.IsoDep")) {
                val passportPref = PassportPref(this)
                val passportNumber = passportPref.getPassportNumber()
                val expirationDate = DateUtils.convertDate(passportPref.getExpiryDate())
                val birthDate = DateUtils.convertDate(passportPref.getBirthDate())
                val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                tx = addWitnesses(tx!!)
                val txMessage = TxUtils.generateSignMsg(tx!!)
                PassportSignTask(tag, bacKey, txMessage, object : PassportCallback {
                    override fun handle(result: String?, error: String?) {
                        val (pubKey, _, _) = TxUtils.parsePublicKey(publicKey)
                        if (result.isNullOrEmpty()) {
                            showToast(R.string.signature_failed)
                            return
                        }
                        val transaction = TxUtils.generateSignedTx(tx!!, result + pubKey)
                        TxUtils.transferTx(transaction, object : RpcCallback<String> {
                            override fun onFailure(errorMessage: String?) {
                                runOnUiThread {
                                    readPassportProgress?.visibility = View.INVISIBLE
                                    showToast(errorMessage)
                                }
                            }
                            override fun onResponse(txHash: String?) {
                                runOnUiThread {
                                    readPassportProgress?.visibility = View.INVISIBLE
                                    showToast(R.string.transfer_tx_send_successfully)
                                    transactionHashView?.visibility = View.VISIBLE
                                    transactionHashView?.text = txHash
                                    viewOnExplorer?.visibility = View.VISIBLE
                                    finishButton?.visibility = View.VISIBLE
                                    viewOnExplorer?.setOnClickListener{
                                        val uri: Uri = Uri.parse("$CKB_EXPLORER_TX_URL/$txHash")
                                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                }
                                Log.d("Transaction", txHash!!)
                            }
                        })
                    }
                }).execute()
            }
        }
    }

    private fun addWitnesses(tx: Transaction): Transaction {
        val witnesses = ArrayList<Any>()
        witnesses.add(Witness())
        for (_i in 1 until tx.inputs.size) {
            witnesses.add("0x")
        }
        val transaction = tx
        transaction.witnesses = witnesses
        return transaction
    }

    private fun showToast(message: Int) {
        Toast.makeText(
            this@TransferActivity,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showToast(message: String?) {
        Toast.makeText(
            this@TransferActivity,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}