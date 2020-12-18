package org.nervos.gw.utils

import android.util.Log
import org.nervos.gw.MainActivity
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

object DateUtils {

    fun convertDate(input: String?): String? {
        return if (input == null) {
            null
        } else try {
            SimpleDateFormat("yyMMdd", Locale.US)
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input))
        } catch (e: ParseException) {
            Log.w(MainActivity::class.java.simpleName, e)
            null
        }
    }
}