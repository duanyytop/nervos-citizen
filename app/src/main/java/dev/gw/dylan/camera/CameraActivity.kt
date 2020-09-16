package dev.gw.dylan.camera

import android.app.Activity
import android.os.Bundle
import dev.gw.dylan.R

class CameraActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        if (null == savedInstanceState) {
            fragmentManager.beginTransaction()
                .replace(R.id.container, CameraFragment.Companion.newInstance())
                .commit()
        }
    }
}