package org.nervos.gw.acount

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.nervos.gw.R
import org.nervos.gw.passport.DocumentData
import org.nervos.gw.passport.PassportConActivity

class MainActivity : AppCompatActivity() {

    companion object {
        @JvmStatic
        val GET_DOC_INFO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
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