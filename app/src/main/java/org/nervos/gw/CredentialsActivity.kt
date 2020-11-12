package org.nervos.gw

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.nervos.gw.passport.DocumentData
import org.nervos.gw.passport.PassportConActivity


class CredentialsActivity : AppCompatActivity() {

    companion object {
        @JvmStatic
        val GET_DOC_INFO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credentials)
        val toolbar = findViewById<Toolbar>(R.id.credentials_toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        findViewById<Button>(R.id.add_credentials).setOnClickListener(View.OnClickListener {
            startActivity(Intent(this, AddPassportActivity::class.java))
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Check if we got documentData and set the documentData attribute
        if (requestCode == GET_DOC_INFO && resultCode == RESULT_OK) {
            val documentData = data?.extras!![DocumentData.Identifier] as DocumentData?
            val intent = Intent(this, PassportConActivity::class.java)
            intent.putExtra(DocumentData.Identifier, documentData)
            startActivity(intent)
        }
    }
}