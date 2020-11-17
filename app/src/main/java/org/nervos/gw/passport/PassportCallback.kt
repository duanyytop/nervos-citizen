package org.nervos.gw.passport

interface PassportCallback {
    fun handle(error: String?)
}