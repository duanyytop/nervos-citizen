package org.nervos.gw.passport

/**
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/passportconnection/PassportConActivity.java
 */

import android.nfc.Tag
import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.PassportService.DEFAULT_MAX_BLOCKSIZE
import org.jmrtd.lds.DG15File
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.LDSFileUtil
import java.io.InputStream
import java.security.PublicKey

class PassportConnection {
    private var ps: PassportService? = null

    /**
     * Opens a connection with the ID by doing BAC
     * Uses hardcoded parameters for now
     *
     * @param tag - NFC tag that started this activity (ID NFC tag)
     * @return PassportService - passportService that has an open connection with the ID
     */
    @Throws(CardServiceException::class)
    fun openConnection(tag: Tag?, docData: DocumentData?): PassportService? {
        return try {
            val nfc = IsoDep.get(tag)
            val cs = CardService.getInstance(nfc)
            ps = PassportService(cs, DEFAULT_MAX_BLOCKSIZE)
            ps!!.open()

            // Get the information needed for BAC from the data provided by OCR
            ps!!.sendSelectApplet(false)
            val bacKey: BACKeySpec = BACKey(docData?.documentNumber, docData?.dateOfBirth, docData?.expiryDate)
            ps!!.doBAC(bacKey)
            ps
        } catch (ex: CardServiceException) {
            try {
                ps!!.close()
            } catch (ex2: Exception) {
                ex2.printStackTrace()
            }
            throw ex
        }
    }

    /**
     * Retrieves the public key used for Active Authentication from datagroup 15.
     *
     * @return Publickey - returns the publickey used for AA
     */
    @Throws(Exception::class)
    fun getAAPublicKey(ps: PassportService?): PublicKey {
        var is15: InputStream? = null
        return try {
            is15 = ps!!.getInputStream(PassportService.EF_DG15)
            val dg15 = DG15File(is15)
            dg15.publicKey
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        } finally {
            is15?.close()
        }
    }

    @get:Throws(Exception::class)
    val aAPublicKey: PublicKey
        get() = getAAPublicKey(ps)

    /**
     * Signs 8 bytes by the passport using the AA functionality.
     *
     * @return byte[] - signed byte array
     */
    @Throws(Exception::class)
    fun signData(ps: PassportService?, data: ByteArray?): ByteArray {
        var is15: InputStream? = null
        return try {
            is15 = ps!!.getInputStream(PassportService.EF_DG15)
            // doAA of JMRTD library only returns signed data, and does not have the AA functionality yet
            // there is no need for sending public key information with the method.
            ps.doAA(null, null, null, data)
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        } finally {
            is15?.close()
        }
    }

    @Throws(Exception::class)
    fun signData(data: ByteArray?): ByteArray {
        return this.signData(ps, data)
    }

    /**
     * Get personal information about a voter from datagroup1.
     * @return Voter - Voter object containing personal data.
     */
    @Throws(Exception::class)
    fun getPerson(ps: PassportService): Person {
        var `is`: InputStream? = null
        return try {
            `is` = ps.getInputStream(PassportService.EF_DG1)
            val dg1 = LDSFileUtil.getLDSFile(PassportService.EF_DG1, `is`) as DG1File
            val mrzInfo = dg1.mrzInfo
            //Replace '<' with spaces since JMRTD does not remove these.
            Person(
                mrzInfo.secondaryIdentifier.replace("<".toRegex(), " ").trim { it <= ' ' },
                mrzInfo.primaryIdentifier.replace("<".toRegex(), " ").trim { it <= ' ' },
                mrzInfo.gender
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        } finally {
            `is`?.close()
        }
    }
}