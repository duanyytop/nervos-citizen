package org.nervos.gw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener{
            finish()
        }

        findViewById<TextView>(R.id.version_value).text = getAppVersionName()
        findViewById<TextView>(R.id.source_code_title).setOnClickListener {
            val uri: Uri = Uri.parse("https://github.com/duanyytop/nervos-citizen")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

    }

    private fun getAppVersionName(): String? {
        var versionName = "1.0"
        try {
            versionName =  packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "v$versionName"
    }
}