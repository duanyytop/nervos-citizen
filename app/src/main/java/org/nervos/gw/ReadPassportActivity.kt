package org.nervos.gw

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import net.sf.scuba.smartcards.CardFileInputStream
import net.sf.scuba.smartcards.CardService
import org.bouncycastle.asn1.x509.Certificate
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.DG2File
import org.jmrtd.lds.MRZInfo
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.nervos.gw.utils.CSCAMasterUtil
import org.nervos.gw.utils.LOG_PASSPORT_TAG
import org.nervos.gw.utils.PrefUtil
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.util.Arrays

class ReadPassportActivity : AppCompatActivity() {

    private var prefUtil: PrefUtil? = null
    private var progressBar: Group? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_passport)

        prefUtil = PrefUtil(this)
        progressBar = findViewById(R.id.read_passport_loading)
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
        super.onPause()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        adapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag = intent.extras!!.getParcelable<Tag>(NfcAdapter.EXTRA_TAG)
            if (listOf(*tag!!.techList).contains("android.nfc.tech.IsoDep")) {
                val passportNumber = prefUtil?.getPassportNumber()
                val expirationDate = prefUtil?.getExpiryDate()
                val birthDate = prefUtil?.getBirthDate()
                if (passportNumber != null && passportNumber.isNotEmpty()
                    && expirationDate != null && expirationDate.isNotEmpty()
                    && birthDate != null && birthDate.isNotEmpty()
                ) {
                    val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                    val nfc = IsoDep.get(tag)
                    nfc.timeout = 5 * 1000
                    ReadTask(this, nfc, bacKey).execute()
                    progressBar?.visibility = View.GONE
                } else {
                    Toast.makeText(this, R.string.nfc_not_supported, Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private class ReadTask(private val context: Context, private val isoDep: IsoDep, private val bacKey: BACKeySpec) :
        AsyncTask<Void?, Void?, Exception?>() {
        private var dg1File: DG1File? = null
        private var dg2File: DG2File? = null
        private var sodFile: SODFile? = null
        private var passiveAuthSuccess = false

        private fun doPassiveAuth() {
            try {
                val digest = MessageDigest.getInstance(sodFile!!.digestAlgorithm)
                val dataHashes = sodFile!!.dataGroupHashes
                val dg1Hash = digest.digest(dg1File!!.encoded)
                val dg2Hash = digest.digest(dg2File!!.encoded)

                if (Arrays.equals(dg1Hash, dataHashes[1]) && Arrays.equals(dg2Hash,dataHashes[2])) {
                    val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
                    keystore.load(null, null)
                    val cf = CertificateFactory.getInstance("X.509")
                    val certMaps: Map<String, Certificate> =
                        CSCAMasterUtil.analysisMasterList(context.assets.open("icaopkd-002-ml-000159.ldif"))
                    var i = 0
                    for ((_, certificate) in certMaps) {
                        val pemCertificate = certificate.encoded
                        val javaCertificate = cf.generateCertificate(ByteArrayInputStream(pemCertificate))
                        keystore.setCertificateEntry(i.toString(), javaCertificate)
                        i++
                    }
                    val docSigningCertificate: X509Certificate? = sodFile?.docSigningCertificate
                    docSigningCertificate?.checkValidity()

                    val cp = cf.generateCertPath(listOf(docSigningCertificate))
                    val pkixParameters = PKIXParameters(keystore)
                    pkixParameters.isRevocationEnabled = false
                    val cpv = CertPathValidator.getInstance(CertPathValidator.getDefaultType())
                    cpv.validate(cp, pkixParameters)
                    val sign = Signature.getInstance(sodFile!!.digestEncryptionAlgorithm)
                    // Initializes this object for verification, using the public key from the given certificate.
                    sign.initVerify(sodFile!!.docSigningCertificate)
                    sign.update(sodFile!!.eContent)
                    passiveAuthSuccess = sign.verify(sodFile!!.encryptedDigest)
                }
            } catch (e: Exception) {
                Log.w(LOG_PASSPORT_TAG, e)
            }
        }

        override fun doInBackground(vararg p0: Void?): Exception? {
            try {
                val cardService = CardService.getInstance(isoDep)
                cardService.open()
                val service = PassportService(cardService, PassportService.DEFAULT_MAX_BLOCKSIZE)
                service.open()
                var paceSucceeded = false
                try {
                    val cardAccessFile =
                        CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
                    val securityInfoCollection: Collection<SecurityInfo> =
                        cardAccessFile.securityInfos
                    for (securityInfo in securityInfoCollection) {
                        if (securityInfo is PACEInfo) {
                            service.doPACE(bacKey, securityInfo.objectIdentifier, PACEInfo.toParameterSpec(
                                securityInfo.parameterId))
                            paceSucceeded = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.w(LOG_PASSPORT_TAG, e)
                }
                service.sendSelectApplet(paceSucceeded)
                if (!paceSucceeded) {
                    try {
                        service.getInputStream(PassportService.EF_COM).read()
                    } catch (e: Exception) {
                        service.doBAC(bacKey)
                    }
                }
                val dg1In: CardFileInputStream = service.getInputStream(PassportService.EF_DG1)
                dg1File = DG1File(dg1In)
                val dg2In: CardFileInputStream = service.getInputStream(PassportService.EF_DG2)
                dg2File = DG2File(dg2In)
                val sodIn: CardFileInputStream = service.getInputStream(PassportService.EF_SOD)
                sodFile = SODFile(sodIn)

                doPassiveAuth()
            } catch (e: Exception) {
                e.printStackTrace()
                return e
            }
            return null
        }

        override fun onPostExecute(result: Exception?) {
            if (result == null) {
                val mrzInfo: MRZInfo? = dg1File?.mrzInfo
                val firstName = mrzInfo?.secondaryIdentifier?.replace("<", " ")
                val lastName = mrzInfo?.primaryIdentifier?.replace("<", " ")
                val gender = mrzInfo?.gender.toString()
                val issuingState = mrzInfo?.issuingState
                val nationality = mrzInfo?.nationality

                val passportInfo = "$firstName, $lastName, $gender, $issuingState, $nationality, Passive Auth: $passiveAuthSuccess"
                Toast.makeText(context, passportInfo, Toast.LENGTH_LONG).show()
            } else {
                exceptionStack(result)?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun exceptionStack(exception: Throwable): String? {
            val s = StringBuilder()
            val exceptionMsg = exception.message
            if (exceptionMsg != null) {
                s.append(exceptionMsg)
                s.append(" - ")
            }
            s.append(exception.javaClass.simpleName)
            val stack = exception.stackTrace
            if (stack.isNotEmpty()) {
                var count = 3
                var first = true
                var skip = false
                var file = ""
                s.append(" (")
                for (element in stack) {
                    if (count > 0 && element.className.startsWith("com.tananaev")) {
                        if (!first) {
                            s.append(" < ")
                        } else {
                            first = false
                        }
                        if (skip) {
                            s.append("... < ")
                            skip = false
                        }
                        if (file == element.fileName) {
                            s.append("*")
                        } else {
                            file = element.fileName
                            s.append(file.substring(0, file.length - 5)) // remove ".java"
                            count -= 1
                        }
                        s.append(":").append(element.lineNumber)
                    } else {
                        skip = true
                    }
                }
                if (skip) {
                    if (!first) {
                        s.append(" < ")
                    }
                    s.append("...")
                }
                s.append(")")
            }
            return s.toString()
        }
    }

}