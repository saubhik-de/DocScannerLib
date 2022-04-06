package org.tensorflow.lite.examples.ocr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.lib.docscanner.camera_module.presentation.ScannerActivity
import org.tensorflow.lite.examples.ocr.databinding.ActivityMainBinding

class MainActivity :Activity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenCamera.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
    }
}