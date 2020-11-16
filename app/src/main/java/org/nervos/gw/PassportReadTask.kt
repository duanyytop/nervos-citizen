package org.nervos.gw

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.Group
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.MRZInfo
import org.jmrtd.lds.PACEInfo

class PassportReadTask(
    private val context: Context,
    private val tag: Tag,
    private val bacKey: BACKeySpec,
    private val progressBar: Group
) : AsyncTask<Void?, Void?, Exception?>() {

    private var dg1File: DG1File? = null
    private var passiveAuthSuccess = false
    private var activeAuthSuccess: Boolean = false

    @Throws(Exception::class)
    override fun doInBackground(vararg p0: Void?): Exception? {
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

            val passportInfo = "$firstName, $lastName, $gender, $issuingState, $nationality, Passive: $passiveAuthSuccess, Active: $activeAuthSuccess"
            Toast.makeText(context, passportInfo, Toast.LENGTH_LONG).show()
            Log.d("Passport", passportInfo)

            progressBar.visibility = View.GONE
        } else {
            result.printStackTrace()
        }
    }

}