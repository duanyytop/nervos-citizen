package org.nervos.gw.db

import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identity")
data class Identity(
    @ColumnInfo(name = "public_key") val publicKey: String = "",
    @ColumnInfo(name = "passport_number") val passportNumber: String = "",
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "gender") val gender: String = "",
    @ColumnInfo(name = "birth") val birth: String = "",
    @ColumnInfo(name = "expiry") val expiry: String = "",
    @ColumnInfo(name = "issuer") val issuer: String = "",
    @ColumnInfo(name = "country") val country: String = "",
    @ColumnInfo(name = "algorithm") val algorithm: String = ""
) {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = BaseColumns._ID) var id: Int = 0
}