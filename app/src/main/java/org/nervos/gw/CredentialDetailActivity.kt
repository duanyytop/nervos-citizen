package org.nervos.gw

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.gson.Gson
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.nervos.ckb.address.Network
import org.nervos.ckb.crypto.Hash
import org.nervos.ckb.service.RpcCallback
import org.nervos.ckb.type.OutPoint
import org.nervos.ckb.type.Script
import org.nervos.ckb.type.Witness
import org.nervos.ckb.type.cell.CellDep
import org.nervos.ckb.type.cell.CellOutput
import org.nervos.ckb.type.transaction.Transaction
import org.nervos.ckb.utils.Numeric
import org.nervos.ckb.utils.address.AddressGenerator
import org.nervos.ckb.utils.address.AddressParser
import org.nervos.gw.db.Identity
import org.nervos.gw.indexer.Collector
import org.nervos.gw.indexer.IndexerApi
import org.nervos.gw.indexer.IndexerCells
import org.nervos.gw.indexer.IndexerCellsCapacity
import org.nervos.gw.indexer.SearchKey
import org.nervos.gw.passport.PassportCallback
import org.nervos.gw.passport.PassportReadTask
import org.nervos.gw.passport.PassportSignTask
import org.nervos.gw.utils.ALGORITHM_ID_ISO9796_2
import org.nervos.gw.utils.DateUtils.convertDate
import org.nervos.gw.utils.HexUtil
import org.nervos.gw.utils.INDEXER_URL
import org.nervos.gw.utils.ISO9796_2_KEY_SIZE
import org.nervos.gw.utils.PASSPORT_CODE_HASH
import org.nervos.gw.utils.PASSPORT_TX_HASH
import org.nervos.gw.utils.PASSPORT_TX_INDEX
import org.nervos.gw.utils.PassportPref
import org.nervos.gw.utils.TxUtils
import java.math.BigDecimal
import java.math.BigInteger

class CredentialDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IDENTITY = "extra_identity"
        const val BUNDLE_IDENTITY = "bundle_identity"
    }

    private val CKB_UNIT = BigInteger.TEN.pow(8)
    private val api = IndexerApi(INDEXER_URL)

    private var credentialTypeView: TextView? = null
    private var credentialNameView: TextView? = null
    private var credentialIssuerView: TextView? = null
    private var credentialCKBAddressView: TextView? = null
    private var credentialCKBBalanceView: TextView? = null
    private var credentialBalanceProgress: ProgressBar? = null
    private var toAddressView: EditText? = null
    private var toAmountView: EditText? = null
    private var transferButton: Button? = null
    private var identity: Identity? = null
    private var tx:Transaction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_detail)
        initToolbar()
        initCredentialInfoView()
        initTransferringView()
    }

    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.credential_detail_toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener{
            startActivity(Intent(this, CredentialsActivity::class.java))
            finish()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun initCredentialInfoView() {
        val bundle = intent.getBundleExtra(BUNDLE_IDENTITY)
        val identityExtra = bundle?.getParcelable<Identity>(EXTRA_IDENTITY)
        if (identityExtra != null) {
            identity = (identityExtra as Identity)
        }

        credentialTypeView = findViewById(R.id.credential_type)
        credentialNameView = findViewById(R.id.credential_name)
        credentialIssuerView = findViewById(R.id.credential_issuer)
        credentialCKBAddressView = findViewById(R.id.credential_ckb_address)
        credentialCKBBalanceView = findViewById(R.id.credential_ckb_balance)
        credentialBalanceProgress = findViewById(R.id.credential_balance_progress)

        credentialTypeView?.text = getString(R.string.credential_type_passport)
        credentialNameView?.text = identity?.name
        credentialIssuerView?.text = identity?.issuer

        val (_, lock, address) = TxUtils.parsePublicKey(identity?.publicKey)
        credentialCKBAddressView?.text = address

        fetchBalance(lock)
    }

    private fun initTransferringView() {
        var toAddress: String? = null
        var toAmount: BigInteger = BigInteger.ZERO
        toAddressView = findViewById(R.id.input_transfer_to_address)
        toAmountView = findViewById(R.id.input_transfer_amount)
        transferButton = findViewById<Button>(R.id.action_transfer)

        toAddressView?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                toAddress = s.toString()
            }
        })

        toAmountView?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val ckbAmount = BigDecimal.valueOf(s.toString().toDouble())
                toAmount = ckbAmount.multiply(BigDecimal.TEN.pow(8)).toBigInteger()
            }
        })

        transferButton?.setOnClickListener{
            if (toAddress.isNullOrEmpty()) {
                Toast.makeText(this, R.string.to_address_not_null, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            } else if (toAmount == BigInteger.ZERO) {
                Toast.makeText(this, R.string.to_amount_not_null, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            transferAction(toAddress!!, toAmount)
        }
    }

    private fun fetchBalance(lock: Script) {
        val callback = object: RpcCallback<IndexerCellsCapacity> {
            override fun onFailure(error: String?) {
                Log.e("Credential", error!!)
            }
            override fun onResponse(cellsCapacity: IndexerCellsCapacity?) {
                runOnUiThread {
                    credentialBalanceProgress?.visibility = View.GONE
                    credentialCKBBalanceView?.visibility = View.VISIBLE
                    val balance = Numeric.toBigInt(cellsCapacity!!.capacity).divide(CKB_UNIT).toString()
                    credentialCKBBalanceView?.text = balance
                }
            }
        }
        api.getCellsCapacity(SearchKey(lock), callback)
    }

    private fun transferAction(toAddress: String, toAmount: BigInteger) {
        val (_, lock, _) = TxUtils.parsePublicKey(identity?.publicKey)
        api.getCells(SearchKey(lock), object: RpcCallback<IndexerCells> {
            override fun onFailure(errorMessage: String?) {
                Log.e("Credential", errorMessage!!)
            }
            override fun onResponse(cells: IndexerCells?) {
                if (cells != null) {
                    val (inputs, changeCapacity) = Collector.collectInputs(cells, toAmount)
                    val toLock = AddressParser.parse(toAddress).script
                    var outputs = listOf(CellOutput(Numeric.toHexStringWithPrefix(toAmount), toLock))
                    var outputsData = listOf("0x")
                    if (changeCapacity > BigInteger.ZERO) {
                        outputs = outputs.plus(CellOutput(Numeric.toHexStringWithPrefix(changeCapacity), lock))
                        outputsData = outputsData.plus("0x")
                    }
                    val cellDeps = listOf(CellDep(OutPoint(PASSPORT_TX_HASH, PASSPORT_TX_INDEX), CellDep.DEP_GROUP))
                    tx = Transaction("0x0", cellDeps, emptyList(), inputs, outputs, outputsData)
                    val intent = Intent(this@CredentialDetailActivity, TransferActivity::class.java)
                    intent.putExtra(TransferActivity.EXTRA_TX, Gson().toJson(tx))
                    intent.putExtra(TransferActivity.EXTRA_PUB_KEY, identity?.publicKey)
                    startActivity(intent)
                }
            }
        })
    }

}