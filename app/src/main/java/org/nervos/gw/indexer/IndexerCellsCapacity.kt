package org.nervos.gw.indexer

import com.google.gson.annotations.SerializedName

class IndexerCellsCapacity {
    @SerializedName("block_hash")
    var blockHash: String? = null

    @SerializedName("block_number")
    var blockNumber: String? = null
    var capacity: String? = null
}