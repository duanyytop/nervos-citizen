package org.nervos.gw.passport

interface PassportCallback {
    fun handle(result: String?, error: String?)
}