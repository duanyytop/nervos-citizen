package org.nervos.gw.indexer

import org.nervos.ckb.service.RpcCallback
import org.nervos.ckb.service.RpcService
import org.nervos.ckb.utils.Numeric
import java.io.IOException
import java.math.BigInteger

class IndexerApi @JvmOverloads constructor(indexerUrl: String?, isDebug: Boolean = false) {
    private val rpcService: RpcService = RpcService(indexerUrl, isDebug)

    @Throws(IOException::class)
    fun getCells(
        searchKey: SearchKey?, order: String?, limit: BigInteger?, afterCursor: String, callback: RpcCallback<IndexerCells>
    ) {
        var list = listOf(
            searchKey,
            order,
            Numeric.toHexStringWithPrefix(limit)
        )
        if ("0x" != afterCursor) {
            list = list.plus(afterCursor)
        }
        rpcService.postAsync("get_cells", list, IndexerCells::class.java, callback)
    }

    @Throws(IOException::class)
    fun getCellsCapacity(searchKey: SearchKey?, callback: RpcCallback<IndexerCellsCapacity>) {
        rpcService.postAsync(
            "get_cells_capacity", listOf(searchKey), IndexerCellsCapacity::class.java, callback
        )
    }
}