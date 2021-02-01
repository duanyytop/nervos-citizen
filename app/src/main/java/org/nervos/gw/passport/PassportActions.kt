package org.nervos.gw.passport

import android.content.Context
import org.bouncycastle.asn1.x509.Certificate
import org.jmrtd.PassportService
import org.jmrtd.Util
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG15File
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.nervos.ckb.utils.Numeric
import org.nervos.gw.utils.CSCAMasterUtil
import org.nervos.gw.utils.HexUtil
import org.nervos.gw.utils.ISO9796SHA1
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.util.Arrays
import javax.crypto.Cipher

class PassportActions(_service: PassportService) {
    private val service = _service

    @Throws(Exception::class)
    fun doPassiveAuth(context: Context): Boolean {
        val dg1File = DG1File(service.getInputStream(PassportService.EF_DG1, PassportService.DEFAULT_MAX_BLOCKSIZE))
        val dg2File = DG2File(service.getInputStream(PassportService.EF_DG2, PassportService.DEFAULT_MAX_BLOCKSIZE))
        val sodFile = SODFile(service.getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE))
        val digest = MessageDigest.getInstance(sodFile.digestAlgorithm)
        val dataHashes = sodFile.dataGroupHashes
        val dg1Hash = digest.digest(dg1File.encoded)
        val dg2Hash = digest.digest(dg2File.encoded)

        if (Arrays.equals(dg1Hash, dataHashes[1]) && Arrays.equals(dg2Hash, dataHashes[2])) {
            val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
            keystore.load(null, null)
            val cf = CertificateFactory.getInstance("X.509")
            val certMaps: Map<String, Certificate> =
                CSCAMasterUtil.analysisMasterList(context.assets.open("icaopkd-002-ml-000159.ldif"))
            var i = 0
            for ((_, certificate) in certMaps) {
                val pemCertificate = certificate.encoded
                val javaCertificate = cf.generateCertificate(
                    ByteArrayInputStream(
                        pemCertificate
                    )
                )
                keystore.setCertificateEntry(i.toString(), javaCertificate)
                i++
            }
            val docSigningCertificate: X509Certificate? = sodFile.docSigningCertificate
            docSigningCertificate?.checkValidity()

            val cp = cf.generateCertPath(listOf(docSigningCertificate))
            val pkixParameters = PKIXParameters(keystore)
            pkixParameters.isRevocationEnabled = false
            val cpv = CertPathValidator.getInstance(CertPathValidator.getDefaultType())
            cpv.validate(cp, pkixParameters)

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(docSigningCertificate!!.tbsCertificate)
            val hash = digest.digest()
            println("TBSCertificate Hash: ${Numeric.toHexString(hash)}")

            val sign = Signature.getInstance(sodFile.digestEncryptionAlgorithm)
            // Initializes this object for verification, using the public key from the given certificate.
            sign.initVerify(docSigningCertificate)
            sign.update(sodFile.eContent)
            return sign.verify(sodFile.encryptedDigest)
        }
        return false
    }

    @Throws(Exception::class)
    fun doActiveAuth(): Boolean {
        val pubKey = getAAPublicKey()
        val random = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val origin = HexUtil.hexStringToByteArray(random)
        val signature = signData(origin)
        return verifySignature(pubKey, origin, signature)
    }

    /**
     * Retrieves the public key used for Active Authentication from data group 15.
     *
     * @return Public key - returns the public key used for AA
     */
    @Throws(Exception::class)
    fun getAAPublicKey(): PublicKey {
        var is15: InputStream? = null
        return try {
            is15 = service.getInputStream(PassportService.EF_DG15)
            val dg15 = DG15File(is15)
            dg15.publicKey
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        } finally {
            is15?.close()
        }
    }

    /**
     * Signs 8 bytes by the passport using the AA functionality.
     *
     * @return byte[] - signed byte array
     */
    @Throws(Exception::class)
    fun signData(data: ByteArray?): ByteArray {
        var is15: InputStream? = null
        return try {
            is15 = service.getInputStream(PassportService.EF_DG15, PassportService.DEFAULT_MAX_BLOCKSIZE)
            service.doAA(null, null, null, data).response
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        } finally {
            is15?.close()
        }
    }

    @Throws(Exception::class)
    fun signTxMessage(txMsg: ByteArray): ByteArray {
        val multiSignature = ByteArray(512)
        var hashPart: ByteArray?
        for (i in 0..3) {
            hashPart = txMsg.copyOfRange(i * 8, i * 8 + 8)
            System.arraycopy(signData(hashPart), 0, multiSignature, i * 128, 128)
        }
        return multiSignature
    }

    @Throws(Exception::class)
    fun verifySignature(pubKey: PublicKey, origin: ByteArray?, signature: ByteArray): Boolean {
        require(!(origin == null || origin.size != 8)) { "AA failed: bad origin" }
        val aaSignature = Signature.getInstance(
            ISO9796SHA1,
            Util.getBouncyCastleProvider()
        )
        val aaDigest = MessageDigest.getInstance("SHA1")
        val aaCipher = Cipher.getInstance("RSA/NONE/NoPadding")
        aaCipher.init(Cipher.DECRYPT_MODE, pubKey)
        aaSignature.initVerify(pubKey)
        val digestLength = aaDigest.digestLength /* should always be 20 */
        val plaintext = aaCipher.doFinal(signature)
        val m1: ByteArray = org.jmrtd.Util.recoverMessage(digestLength, plaintext)
        aaSignature.update(m1)
        aaSignature.update(origin)
        return aaSignature.verify(signature)
    }

    @Throws(Exception::class)
    fun verifyTxHashSignature(pubKey: PublicKey, txHash: ByteArray, signature: ByteArray): Boolean {
        var hashPart: ByteArray?
        var signaturePart: ByteArray?
        for (i in 0..3) {
            hashPart = txHash.copyOfRange(i * 8, i * 8 + 8)
            signaturePart = signature.copyOfRange(i * 128, i * 128 + 128)
            if (!verifySignature(pubKey, hashPart, signaturePart)) {
                return false
            }
        }
        return true
    }

}