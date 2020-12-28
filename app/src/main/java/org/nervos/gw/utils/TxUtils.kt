package org.nervos.gw.utils

import org.nervos.ckb.address.Network
import org.nervos.ckb.crypto.Blake2b
import org.nervos.ckb.crypto.Hash
import org.nervos.ckb.service.RpcCallback
import org.nervos.ckb.service.RpcService
import org.nervos.ckb.type.Script
import org.nervos.ckb.type.Witness
import org.nervos.ckb.type.fixed.UInt64
import org.nervos.ckb.type.transaction.Transaction
import org.nervos.ckb.utils.Convert
import org.nervos.ckb.utils.Numeric
import org.nervos.ckb.utils.Serializer
import org.nervos.ckb.utils.address.AddressGenerator
import java.util.ArrayList

object TxUtils {

    private const val SIGNATURE_PLACEHOLDER_LEN = 652

    fun generateSignMsg(tx: Transaction): String {
        val witnesses = tx.witnesses
        if (witnesses.size < 1) {
            throw RuntimeException("Need at least one witness!")
        }
        if (witnesses[0]?.javaClass != Witness::class.java) {
            throw RuntimeException("First witness must be of Witness type!")
        }
        val txHash: String = tx.computeHash()
        val emptiedWitness = witnesses[0] as Witness
        emptiedWitness.lock = "00".repeat(SIGNATURE_PLACEHOLDER_LEN)
        val witnessTable = Serializer.serializeWitnessArgs(emptiedWitness)
        val blake2b = Blake2b()
        blake2b.update(Numeric.hexStringToByteArray(txHash))
        blake2b.update(UInt64(witnessTable.length).toBytes())
        blake2b.update(witnessTable.toBytes())
        for (i in 1 until witnesses.size) {
            val bytes: ByteArray = if (witnesses[i]?.javaClass == Witness::class.java) {
                Serializer.serializeWitnessArgs(witnesses[i] as Witness).toBytes()
            } else {
                Numeric.hexStringToByteArray(witnesses[i] as String)
            }
            blake2b.update(UInt64(bytes.size).toBytes())
            blake2b.update(bytes)
        }
        return blake2b.doFinalString()
    }

    fun generateSignedTx(tx: Transaction, signature: String): Transaction {
        (tx.witnesses[0] as Witness).lock = signature
        val signedWitness: MutableList<String?> = ArrayList()
        for (witness in tx.witnesses) {
            if (witness?.javaClass == Witness::class.java) {
                signedWitness.add(
                    Numeric.toHexString(
                        Serializer.serializeWitnessArgs(witness as Witness).toBytes()
                    )
                )
            } else {
                signedWitness.add(witness as String)
            }
        }
        tx.witnesses = signedWitness
        return tx
    }

    fun transferTx(tx: Transaction, callback: RpcCallback<String>) {
        val rpc = RpcService(NODE_URL, true)
        rpc.postAsync(
            "send_transaction", listOf(Convert.parseTransaction(tx)),
            String::class.java, callback
        )
    }

    fun parsePublicKey(publicKey: String?): Triple<String, Script, String> {
        if (publicKey == null) {
            return Triple("", Script(), "")
        }
        val index = publicKey.indexOf("-")
        // Public N must be little endian
        var modulus = publicKey.substring(0, index)
        val modulusBytes = Numeric.hexStringToByteArray(modulus)
        modulusBytes.reverse()
        modulus = Numeric.toHexStringNoPrefix(modulusBytes)
        val publicExponent = publicKey.substring(index + 1)
        val pubKey = HexUtil.u32LittleEndian(ALGORITHM_ID_ISO9796_2.toLong()) + HexUtil.u32LittleEndian(
            ISO9796_2_KEY_SIZE.toLong()) + HexUtil.u32LittleEndian(publicExponent.toLong()) + modulus
        val args = Numeric.prependHexPrefix(Hash.blake160(pubKey))
        val lock = Script(PASSPORT_CODE_HASH, args, Script.TYPE)
        val address = AddressGenerator.generateFullAddress(Network.TESTNET, lock)
        return Triple(pubKey, lock, address)
    }
}