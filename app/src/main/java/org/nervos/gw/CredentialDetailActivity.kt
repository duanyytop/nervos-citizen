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
        identity = bundle?.getParcelable<Identity>(EXTRA_IDENTITY) as Identity

        credentialTypeView = findViewById(R.id.credential_type)
        credentialNameView = findViewById(R.id.credential_name)
        credentialIssuerView = findViewById(R.id.credential_issuer)
        credentialCKBAddressView = findViewById(R.id.credential_ckb_address)
        credentialCKBBalanceView = findViewById(R.id.credential_ckb_balance)
        credentialBalanceProgress = findViewById(R.id.credential_balance_progress)

        credentialTypeView?.text = getString(R.string.credential_type_passport)
        credentialNameView?.text = identity?.name
        credentialIssuerView?.text = identity?.issuer

        val (_, lock, address) = parsePublicKey(identity?.publicKey)
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
        val (_, lock, _) = parsePublicKey(identity?.publicKey)
        api.getCells(SearchKey(lock), object: RpcCallback<IndexerCells> {
            override fun onFailure(errorMessage: String?) {
                Log.e("Credential", errorMessage!!)
            }
            override fun onResponse(cells: IndexerCells?) {
                if (cells != null) {
                    val (inputs, changeCapacity) = Collector.collectInputs(cells, toAmount)
                    val toLock = AddressParser.parse(toAddress).script
                    val outputs = listOf(CellOutput(Numeric.toHexStringWithPrefix(toAmount), toLock))
                    val outputsData = listOf("0x")
                    val witnesses = listOf(Witness())
                    for (_i in 1 until inputs.size) {
                        witnesses.plus("0x")
                    }
                    if (changeCapacity > BigInteger.ZERO) {
                        outputs.plus(CellOutput(Numeric.toHexStringWithPrefix(changeCapacity), lock))
                        outputsData.plus("0x0")
                    }
                    val cellDeps = listOf(CellDep(OutPoint(PASSPORT_TX_HASH, PASSPORT_TX_INDEX), CellDep.DEP_GROUP))
                    tx = Transaction("0x0", cellDeps, emptyList(), inputs, outputs, outputsData, witnesses)
                    transferButton?.text = getString(R.string.transfer_request_sign)
                }
            }
        })
    }

    private fun parsePublicKey(publicKey: String?): Triple<String, Script, String> {
        if (publicKey == null) {
            return Triple("", Script(), "")
        }
        val index = publicKey.indexOf("-")
        val modulus = publicKey.substring(0, index)
        val publicExponent = publicKey.substring(index + 1)
        val pubKey = HexUtil.u32LittleEndian(ALGORITHM_ID_ISO9796_2.toLong()) + HexUtil.u32LittleEndian(
            ISO9796_2_KEY_SIZE.toLong()) + HexUtil.u32LittleEndian(publicExponent.toLong()) + modulus
        val args = Numeric.prependHexPrefix(Hash.blake160(pubKey))
        val lock = Script(PASSPORT_CODE_HASH, args, Script.TYPE)
        val address = AddressGenerator.generateFullAddress(Network.TESTNET, lock)
        return Triple(pubKey, lock, address)
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
            val tag = intent.extras!!.getParcelable<Tag>(NfcAdapter.EXTRA_TAG)
            if (listOf(*tag!!.techList).contains("android.nfc.tech.IsoDep")) {
                val passportPref = PassportPref(this)
                val passportNumber = passportPref.getPassportNumber()
                val expirationDate = convertDate(passportPref.getExpiryDate())
                val birthDate = convertDate(passportPref.getBirthDate())
                val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                val txMessage = TxUtils.generateSignMsg(tx!!)
                PassportSignTask(tag, bacKey, txMessage, object : PassportCallback {
                    override fun handle(result: String?, error: String?) {
                        transferButton?.text = getString(R.string.transfer_sign_complete)
                        val (pubKey, _, _) = parsePublicKey(identity?.publicKey)
                        val transaction = TxUtils.generateSignedTx(tx!!, result + pubKey)
                        TxUtils.transferTx(transaction, object: RpcCallback<String> {
                            override fun onFailure(errorMessage: String?) {
                                Toast.makeText(this@CredentialDetailActivity, errorMessage, Toast.LENGTH_LONG).show()
                            }
                            override fun onResponse(txHash: String?) {
                                transferButton?.text = getString(R.string.transfer_tx_send_successfully)
                                Log.d("Transaction", txHash!!)
                            }
                        })
                    }
                }).execute()
            }
        }
    }
}