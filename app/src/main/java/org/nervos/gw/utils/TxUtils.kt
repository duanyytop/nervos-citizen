package org.nervos.gw.utils

import org.nervos.ckb.crypto.Blake2b
import org.nervos.ckb.service.RpcCallback
import org.nervos.ckb.service.RpcService
import org.nervos.ckb.type.Witness
import org.nervos.ckb.type.fixed.UInt64
import org.nervos.ckb.type.transaction.Transaction
import org.nervos.ckb.utils.Convert
import org.nervos.ckb.utils.Numeric
import org.nervos.ckb.utils.Serializer
import java.util.ArrayList

object TxUtils {

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
        emptiedWitness.lock = Witness.SIGNATURE_PLACEHOLDER
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
}