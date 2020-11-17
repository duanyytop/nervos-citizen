package org.nervos.gw.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

const val KEY_HISTORY = "history"
const val KEY_URL_HISTORY = "history_url"

class HistoryPref(context: Context) {
    private var preferences: SharedPreferences? = null
    private var gson: Gson? = null

    init {
        preferences = context.getSharedPreferences(KEY_HISTORY, Context.MODE_PRIVATE)
        gson = Gson()
    }

    fun putHistoryUrls(urls: List<String>) {
        if (urls.isNotEmpty()) {
            preferences?.edit()?.putString(KEY_URL_HISTORY, gson?.toJson(urls))?.apply()
        }
    }

    fun getHistoryUrls(): List<String>? {
        return gson?.fromJson(preferences?.getString(KEY_URL_HISTORY, null),
            object : TypeToken<List<String>>() {}.type
        )
    }

}

