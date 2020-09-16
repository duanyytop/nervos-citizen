package dev.gw.dylan.wallet

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import dev.gw.dylan.R
import dev.gw.dylan.camera.CameraActivity
import dev.gw.dylan.passport.DocumentData
import dev.gw.dylan.passport.PassportConActivity

class MainActivity : AppCompatActivity() {

    companion object {
        @JvmStatic
        val GET_DOC_INFO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<Button>(R.id.start_ocr).setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivityForResult(intent, GET_DOC_INFO)
        }
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