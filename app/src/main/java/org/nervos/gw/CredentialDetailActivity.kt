package org.nervos.gw

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import org.nervos.ckb.address.Network
import org.nervos.ckb.crypto.Hash
import org.nervos.ckb.service.RpcCallback
import org.nervos.ckb.type.Script
import org.nervos.ckb.utils.Numeric
import org.nervos.ckb.utils.address.AddressGenerator
import org.nervos.gw.db.Identity
import org.nervos.gw.indexer.IndexerApi
import org.nervos.gw.indexer.IndexerCellsCapacity
import org.nervos.gw.indexer.SearchKey
import org.nervos.gw.passport.ReadPassportActivity
import org.nervos.gw.utils.ALGORITHM_ID_ISO9796_2
import org.nervos.gw.utils.HexUtil
import org.nervos.gw.utils.INDEXER_URL
import org.nervos.gw.utils.ISO9796_2_KEY_SIZE
import org.nervos.gw.utils.PASSPORT_CODE_HASH
import java.math.BigInteger

class CredentialDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IDENTITY = "extra_identity"
        const val BUNDLE_IDENTITY = "bundle_identity"
    }

    private val CKB_UNIT = BigInteger.TEN.pow(8)

    private var credentialTypeView: TextView? = null
    private var credentialNameView: TextView? = null
    private var credentialIssuerView: TextView? = null
    private var credentialCKBAddressView: TextView? = null
    private var credentialCKBBalanceView: TextView? = null
    private var credentialBalanceProgress: ProgressBar? = null
    private var toAddressView: EditText? = null
    private var toAmountView: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_detail)
        initToolbar()
        initView()
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

    private fun initView() {
        val bundle = intent.getBundleExtra(BUNDLE_IDENTITY)
        val identity = bundle?.getParcelable<Identity>(EXTRA_IDENTITY) as Identity

        credentialTypeView = findViewById(R.id.credential_type)
        credentialNameView = findViewById(R.id.credential_name)
        credentialIssuerView = findViewById(R.id.credential_issuer)
        credentialCKBAddressView = findViewById(R.id.credential_ckb_address)
        credentialCKBBalanceView = findViewById(R.id.credential_ckb_balance)
        credentialBalanceProgress = findViewById(R.id.credential_balance_progress)
        toAddressView = findViewById(R.id.transfer_to_address)
        toAmountView = findViewById(R.id.transfer_amount)

        credentialTypeView?.text = getString(R.string.credential_type_passport)
        credentialNameView?.text = identity.name
        credentialIssuerView?.text = identity.issuer

        val (_, lock, address) = parsePublicKey(identity.publicKey)
        credentialCKBAddressView?.text = address

        fetchBalance(lock)

        findViewById<Button>(R.id.action_transfer).setOnClickListener{
            transferAction()
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
        val api = IndexerApi(INDEXER_URL)
        api.getCellsCapacity(SearchKey(lock), callback)
    }

    private fun transferAction() {
        // TODO: transfer ckb with passport lock script
    }

    private fun parsePublicKey(publicKey: String): Triple<String, Script, String> {
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
}