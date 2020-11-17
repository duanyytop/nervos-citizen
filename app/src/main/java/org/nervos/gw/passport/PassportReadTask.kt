package org.nervos.gw.passport

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.MRZInfo
import org.jmrtd.lds.PACEInfo
import org.nervos.gw.R
import org.nervos.gw.db.Identity
import org.nervos.gw.db.IdentityDatabase
import org.nervos.gw.utils.ISO9796SHA1

class PassportReadTask(
    private val context: Context,
    private val tag: Tag,
    private val bacKey: BACKeySpec,
    private val callback: PassportCallback
) : AsyncTask<Void?, Void?, String?>() {

    private var dg1File: DG1File? = null
    private var passiveAuthSuccess = false
    private var activeAuthSuccess: Boolean = false
    private var publicKey: String? = null

    @Throws(Exception::class)
    override fun doInBackground(vararg p0: Void?): String? {
        try {
            val nfc = IsoDep.get(tag)
            val cardService = CardService.getInstance(nfc)
            cardService.open()

            val service = PassportService(cardService, PassportService.DEFAULT_MAX_BLOCKSIZE)
            service.open()
            var paceSucceeded = false
            try {
                val cardAccessFile =
                    CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
                val securityInfoCollection = cardAccessFile.securityInfos
                for (securityInfo in securityInfoCollection) {
                    if (securityInfo is PACEInfo) {
                        service.doPACE(
                            bacKey,
                            securityInfo.objectIdentifier,
                            PACEInfo.toParameterSpec(securityInfo.parameterId),
                        )
                        paceSucceeded = true
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            service.sendSelectApplet(paceSucceeded)
            if (!paceSucceeded) {
                try {
                    service.getInputStream(PassportService.EF_COM).read()
                } catch (e: java.lang.Exception) {
                    service.doBAC(bacKey)
                }
            }
            dg1File = DG1File(service.getInputStream(PassportService.EF_DG1))
            val actions = PassportActions(service)
            passiveAuthSuccess = actions.doPassiveAuth(context)
            activeAuthSuccess = actions.doActiveAuth()
            publicKey = actions.getAAPublicKey().toString()
            return saveData()
        } catch (e: Exception) {
            e.printStackTrace()
            return context.getString(R.string.passport_read_error)
        }
    }

    override fun onPostExecute(error: String?) {
        callback.handle(error)
    }

    private fun saveData(): String? {
        val mrz: MRZInfo? = dg1File?.mrzInfo
        val firstName = mrz?.secondaryIdentifier?.replace("<", "")
        val lastName = mrz?.primaryIdentifier?.replace("<", "")
        val name = "$firstName $lastName"
        val identity = Identity(publicKey!!, bacKey.documentNumber, name, mrz?.gender.toString(),
            bacKey.dateOfBirth, bacKey.dateOfExpiry, mrz?.issuingState!!,
            mrz.nationality!!, ISO9796SHA1
        )
        val db = IdentityDatabase.instance(context.applicationContext).identityDao()
        if (db.findByPublicKey(publicKey!!) == null) {
            db.insert(identity)
            return null
        }
        return context.getString(R.string.passport_exist)
    }

}