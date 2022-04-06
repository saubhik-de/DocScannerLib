package com.lib.docscanner.camera_module.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import com.lib.docscanner.MainActivity
import com.lib.docscanner.R
import com.lib.docscanner.camera_module.exceptions.NullCorners
import java.io.File
import java.io.FileOutputStream


class ScannerActivity : BaseScannerActivity() {

    private lateinit var images : MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        images = ArrayList()
    }

    override fun onError(throwable: Throwable) {
        when (throwable) {
            is NullCorners -> Toast.makeText(
                this,
                R.string.null_corners, Toast.LENGTH_LONG
            )
                .show()
            else -> Toast.makeText(this, throwable.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDocumentAccepted(bitmap: Bitmap) {
        images.add(writeBitmapToFile(bitmap))
        if(images.size>=2){
            binding.btnProceed.visibility = View.VISIBLE
        }
    }

    override fun onClose() {
        finish()
    }

    override fun onProceed() {
       /* val stream1 = ByteArrayOutputStream()
        images[0].compress(Bitmap.CompressFormat.PNG, 100, stream1)
        val byteArray1: ByteArray = stream1.toByteArray()

        val stream2 = ByteArrayOutputStream()
        images[1].compress(Bitmap.CompressFormat.PNG, 100, stream2)
        val byteArray2: ByteArray = stream2.toByteArray()*/

        val in1 = Intent(this, MainActivity::class.java)
        in1.putExtra("image1", images[0])
        in1.putExtra("image2", images[1])
        startActivity(in1)
    }

    private fun writeBitmapToFile(bitmap: Bitmap) : String{
        val file_path = getFilesDir().absolutePath +
                "/DocScan"
        val dir = File(file_path)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "doc_" + System.currentTimeMillis().toString() + ".png")
        val fOut = FileOutputStream(file)

        bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut)
        fOut.flush()
        fOut.close()

        return file.absolutePath
    }
}
