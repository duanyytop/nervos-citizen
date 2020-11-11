package org.nervos.gw.passport

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class DocumentData(
    var documentNumber: String = "",
    var expiryDate: String = "",
    var dateOfBirth: String = ""
) : Parcelable {
    companion object {
        @JvmStatic
        val Identifier = "docData"
    }
}