package org.nervos.gw.indexer

import com.google.gson.annotations.SerializedName
import org.nervos.ckb.type.OutPoint
import org.nervos.ckb.type.cell.CellOutput

class IndexerCells {
    var objects: List<Cell>? = null

    @SerializedName("last_cursor")
    var lastCursor: String? = null

    class Cell {
        @SerializedName("block_number")
        var blockNumber: String? = null

        @SerializedName("out_point")
        var outPoint: OutPoint? = null
        var output: CellOutput? = null

        @SerializedName("output_data")
        var outputData: String? = null

        @SerializedName("tx_index")
        var txIndex: String? = null
    }
}