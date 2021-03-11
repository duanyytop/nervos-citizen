package org.nervos.gw.passport

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.nervos.ckb.utils.Numeric

class PassportSignTask(
    private val tag: Tag,
    private val bacKey: BACKeySpec,
    private val txMessage: String,
    private val callback: PassportCallback
) : AsyncTask<Void?, Void?, String?>() {

    @Throws(Exception::class)
    override fun doInBackground(vararg p0: Void?): String? {
        try {
            val nfc = IsoDep.get(tag)
            val cardService = CardService.getInstance(nfc)
            cardService.open()

            val service = PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, false, true)
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
            val actions = PassportActions(service)
            val txMsg = Numeric.hexStringToByteArray(txMessage)
            return Numeric.toHexString(actions.signTxMessage(txMsg))
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onPostExecute(signature: String?) {
        callback.handle(signature, null)
    }


}