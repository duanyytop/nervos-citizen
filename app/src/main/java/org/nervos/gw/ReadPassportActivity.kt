package org.nervos.gw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.nervos.gw.databinding.ActivityReadPassportBinding

class ReadPassportActivity : AppCompatActivity() {

    private var binding: ActivityReadPassportBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadPassportBinding.inflate(layoutInflater)
        setContentView(binding?.root)
    }

}