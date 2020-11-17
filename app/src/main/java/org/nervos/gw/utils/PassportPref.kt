package org.nervos.gw.utils

import android.content.Context
import android.content.SharedPreferences

const val KEY_PREFERENCES = "data"
const val KEY_PASSPORT_NUMBER = "passportNumber"
const val KEY_EXPIRATION_DATE = "expirationDate"
const val KEY_BIRTH_DATE = "birthDate"

class PassportPref(context: Context) {
    private var preferences: SharedPreferences? = null

    init {
        this.preferences = context.getSharedPreferences(KEY_PREFERENCES, Context.MODE_PRIVATE)
    }

    fun putPassportNumber(passportNumber :String?) {
        if (passportNumber != null) {
            preferences?.edit()?.putString(KEY_PASSPORT_NUMBER, passportNumber)?.apply()
        }
    }

    fun putPassportExpiryDate(dateOfExpiry: String?) {
        if (dateOfExpiry != null) {
            preferences?.edit()?.putString(KEY_EXPIRATION_DATE, dateOfExpiry)?.apply()
        }
    }

    fun putPassportBirthDate(dateOfBirth: String?) {
        if (dateOfBirth != null) {
            preferences?.edit()?.putString(KEY_BIRTH_DATE, dateOfBirth)?.apply()
        }
    }

    fun getPassportNumber(): String? {
        return preferences?.getString(KEY_PASSPORT_NUMBER, null)
    }

    fun getExpiryDate(): String? {
        return preferences?.getString(KEY_EXPIRATION_DATE, null)
    }

    fun getBirthDate(): String? {
        return preferences?.getString(KEY_BIRTH_DATE, null)
    }

}

