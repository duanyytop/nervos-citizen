package org.nervos.gw.indexer

import com.google.gson.annotations.SerializedName
import org.nervos.ckb.type.Script

class SearchKey {
    private var script: Script

    @SerializedName("script_type")
    var scriptType: String

    constructor(script: Script, scriptType: String) {
        this.script = script
        this.scriptType = scriptType
    }

    constructor(script: Script) {
        this.script = script
        scriptType = "lock"
    }
}