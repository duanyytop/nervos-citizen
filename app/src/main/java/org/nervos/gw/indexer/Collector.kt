package org.nervos.gw.indexer

import org.nervos.ckb.type.cell.CellInput
import org.nervos.ckb.utils.Numeric
import java.lang.Exception
import java.math.BigInteger
import kotlin.jvm.Throws

object Collector {
    val FEE: BigInteger = BigInteger.valueOf(2000)
    val MIN_CAPACITY: BigInteger = BigInteger.valueOf(61).multiply(BigInteger.TEN.pow(8))

    @Throws
    fun collectInputs(cells: IndexerCells, needCapacity: BigInteger): Pair<List<CellInput>, BigInteger> {
        var inputCapacity = BigInteger.ZERO
        val needCapacitySum = needCapacity + FEE
        val inputs = ArrayList<CellInput>()
        for (cell in cells.objects!!) {
            inputCapacity += Numeric.toBigInt(cell.output?.capacity)
            inputs.add(CellInput(cell.outPoint, "0x0"))
            if (inputCapacity == needCapacitySum || (inputCapacity - MIN_CAPACITY) >= needCapacitySum) {
                break
            }
        }
        if (inputCapacity < needCapacitySum) {
            throw Exception("Capacity not enough")
        }
        if (inputCapacity > needCapacitySum && (inputCapacity - MIN_CAPACITY) < needCapacitySum) {
            throw Exception("Change capacity not enough")
        }
        val changeCapacity = if (inputCapacity > needCapacitySum) {
            inputCapacity - needCapacitySum
        } else {
            BigInteger.ZERO
        }
        return Pair(inputs, changeCapacity)
    }

}