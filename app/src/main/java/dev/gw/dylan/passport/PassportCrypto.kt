package dev.gw.dylan.passport

import dev.gw.dylan.utils.PASSPORT_LOCK_CODE_HASH
import dev.gw.dylan.utils.Util
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.jmrtd.JMRTDSecurityProvider
import org.nervos.ckb.address.Network
import org.nervos.ckb.crypto.Hash
import org.nervos.ckb.type.Script
import org.nervos.ckb.utils.Numeric
import org.nervos.ckb.utils.address.AddressGenerator
import java.io.StringWriter
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Arrays
import javax.crypto.Cipher

object PassportCrypto {

    @Throws(Exception::class)
    fun signTxHash(txHash: ByteArray?, pcon: PassportConnection): ByteArray {
        val multiSignature = ByteArray(512)
        var hashPart: ByteArray?
        for (i in 0..3) {
            hashPart = Arrays.copyOfRange(txHash, i * 8, i * 8 + 8)
            System.arraycopy(pcon.signData(hashPart), 0, multiSignature, i * 128, 128)
        }
        return multiSignature
    }

    @Throws(Exception::class)
    fun publicKeyToPemFormat(pubKey: PublicKey): String {
        val writer = StringWriter()
        val pemWriter = PemWriter(writer)
        pemWriter.writeObject(PemObject("PUBLIC KEY", pubKey.encoded))
        pemWriter.flush()
        pemWriter.close()
        return writer.toString()
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

    @Throws(Exception::class)
    fun verifySignature(pubKey: PublicKey, origin: ByteArray?, signature: ByteArray): Boolean {
        require(!(origin == null || origin.size != 8)) { "AA failed: bad origin" }
        val aaSignature = Signature.getInstance(
            "SHA1WithRSA/ISO9796-2",
            JMRTDSecurityProvider.getBouncyCastleProvider()
        )
        val aaDigest = MessageDigest.getInstance("SHA1")
        val aaCipher = Cipher.getInstance("RSA/NONE/NoPadding")
        aaCipher.init(Cipher.DECRYPT_MODE, pubKey)
        aaSignature.initVerify(pubKey)
        val digestLength = aaDigest.digestLength /* should always be 20 */
        val plaintext = aaCipher.doFinal(signature)
        val m1: ByteArray = org.jmrtd.Util.recoverMessage(digestLength, plaintext)
        println(Util.byteArrayToHexString(m1))
        aaSignature.update(m1)
        aaSignature.update(origin)
        return aaSignature.verify(signature)
    }


    private fun pubKeyToBlake160(pubKey: RSAPublicKey): String {
        val modulus = pubKey.modulus.toByteArray()
        val publicExponent = pubKey.publicExponent.toByteArray()
        val prefix = BigInteger.valueOf(publicExponent.size.toLong()).toByteArray()
        return Numeric.toHexString(Hash.blake2b(prefix + modulus + publicExponent))
    }

    fun pubKeyToAddress(pubKey: RSAPublicKey): String {
        val args = pubKeyToBlake160(pubKey)
        val lockScript = Script(PASSPORT_LOCK_CODE_HASH, args, Script.TYPE)
        return AddressGenerator.generateFullAddress(Network.TESTNET, lockScript)
    }
}