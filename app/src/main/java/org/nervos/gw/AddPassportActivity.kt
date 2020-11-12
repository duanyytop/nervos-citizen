package org.nervos.gw

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddPassportActivity : AppCompatActivity() {

    private val KEY_PASSPORT_NUMBER = "passportNumber"
    private val KEY_EXPIRATION_DATE = "expirationDate"
    private val KEY_BIRTH_DATE = "birthDate"

    private var passportNumberView: EditText? = null
    private var expirationDateView: EditText? = null
    private var birthDateView: EditText? = null
    private var passportNumberFromIntent = false

    private var preferences: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_passport)

        initView()
    }

    private fun initView() {
        preferences = getSharedPreferences("data", Context.MODE_PRIVATE)

        val dateOfBirth = intent.getStringExtra("dateOfBirth")
        val dateOfExpiry = intent.getStringExtra("dateOfExpiry")
        val passportNumber = intent.getStringExtra("passportNumber")

        if (dateOfBirth != null) {
            preferences?.edit()?.putString(KEY_BIRTH_DATE, dateOfBirth)?.apply()
        }
        if (dateOfExpiry != null) {
            preferences?.edit()?.putString(KEY_EXPIRATION_DATE, dateOfExpiry)?.apply()
        }
        if (passportNumber != null) {
            preferences?.edit()?.putString(KEY_PASSPORT_NUMBER, passportNumber)?.apply()
            passportNumberFromIntent = true
        }

        passportNumberView = findViewById(R.id.input_passport_number)
        expirationDateView = findViewById(R.id.input_passport_expiry_date)
        birthDateView = findViewById(R.id.input_passport_birth_date)

        passportNumberView?.setText(preferences?.getString(KEY_PASSPORT_NUMBER, null))
        expirationDateView?.setText(preferences?.getString(KEY_EXPIRATION_DATE, null))
        birthDateView?.setText(preferences?.getString(KEY_BIRTH_DATE, null))

        passportNumberView?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                preferences?.edit()?.putString(KEY_PASSPORT_NUMBER, s.toString())?.apply()
            }
        })

        expirationDateView?.setOnClickListener {
            val c = loadDate(expirationDateView)
            DatePickerDialog(
                this, { _, year: Int, month: Int, dayOfMonth: Int ->
                    saveDate(expirationDateView, year, month, dayOfMonth, KEY_EXPIRATION_DATE)
                }, c!![Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH]
            ).show()
        }

        birthDateView?.setOnClickListener {
            val c = loadDate(birthDateView)
            DatePickerDialog(this, {  _, year: Int, month: Int, dayOfMonth: Int ->
                    saveDate(birthDateView, year, month, dayOfMonth, KEY_BIRTH_DATE)
                }, c!![Calendar.YEAR], c[Calendar.MONTH],
                c[Calendar.DAY_OF_MONTH]
            ).show()
        }
    }


    private fun loadDate(editText: EditText?): Calendar? {
        val calendar = Calendar.getInstance()
        if (editText?.text.toString().isNotEmpty()) {
            try {
                calendar.timeInMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(editText?.text.toString()).time
            } catch (e: ParseException) {
                Log.w(MainActivity::class.java.simpleName, e)
            }
        }
        return calendar
    }

    private fun saveDate(editText: EditText?, year: Int, month: Int, dayOfMonth: Int, key: String) {
        val value = String.format(Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth)
        preferences?.edit()?.putString(key, value)?.apply()
        editText?.setText(value)
    }


}