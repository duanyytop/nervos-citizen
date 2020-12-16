package org.nervos.gw

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import org.nervos.ckb.address.Network
import org.nervos.ckb.crypto.Hash
import org.nervos.ckb.type.Script
import org.nervos.ckb.utils.Numeric
import org.nervos.ckb.utils.address.AddressGenerator
import org.nervos.gw.db.Identity
import org.nervos.gw.utils.ALGORITHM_ID_ISO9796_2
import org.nervos.gw.utils.HexUtil
import org.nervos.gw.utils.ISO9796_2_KEY_SIZE
import org.nervos.gw.utils.PASSPORT_CODE_HASH
import org.w3c.dom.Entity

class CredentialDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IDENTITY = "extra_identity"
        const val BUNDLE_IDENTITY = "bundle_identity"
    }

    private var credentialTypeView: TextView? = null
    private var credentialNameView: TextView? = null
    private var credentialIssuerView: TextView? = null
    private var credentialCKBAddressView: TextView? = null
    private var credentialCKBBalanceView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_detail)
        initView()
    }

    private fun initView() {
        val toolbar = findViewById<Toolbar>(R.id.credential_detail_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val bundle = intent.getBundleExtra(BUNDLE_IDENTITY)
        val identity = bundle?.getParcelable<Identity>(EXTRA_IDENTITY) as Identity

        credentialTypeView = findViewById(R.id.credential_type)
        credentialNameView = findViewById(R.id.credential_name)
        credentialIssuerView = findViewById(R.id.credential_issuer)
        credentialCKBAddressView = findViewById(R.id.credential_ckb_address)
        credentialCKBBalanceView = findViewById(R.id.credential_ckb_balance)

        credentialTypeView?.text = getString(R.string.credential_type_passport)
        credentialNameView?.text = identity.name
        credentialIssuerView?.text = identity.issuer
        credentialCKBAddressView?.text = generateAddress(identity.publicKey)
    }

    private fun generateAddress(publicKey: String): String {
        val index = publicKey.indexOf("-")
        val modulus = publicKey.substring(0, index)
        val publicExponent = publicKey.substring(index + 1)
        val pubKey = HexUtil.u32LittleEndian(ALGORITHM_ID_ISO9796_2.toLong()) + HexUtil.u32LittleEndian(
            ISO9796_2_KEY_SIZE.toLong()) + HexUtil.u32LittleEndian(publicExponent.toLong()) + modulus
        val args = Hash.blake160(pubKey)
        val lock = Script(PASSPORT_CODE_HASH, args, Script.TYPE)
        return AddressGenerator.generateFullAddress(Network.TESTNET, lock)
    }
}