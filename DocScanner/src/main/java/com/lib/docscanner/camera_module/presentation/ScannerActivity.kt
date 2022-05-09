package com.lib.docscanner.camera_module.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import com.lib.docscanner.MainActivity
import com.lib.docscanner.R
import com.lib.docscanner.camera_module.exceptions.NullCorners
import java.io.*


class ScannerActivity : BaseScannerActivity() {

    private lateinit var images: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        images = ArrayList()
       /* val outDir =
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.absolutePath + "/tesseract/tessdata/eng.traineddata"
        val outFile = File(outDir)
        if (!outFile.exists()) {
            copyAssets()
        }*/
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
        if (images.size >= 1) {
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
        for (img: String in images) {
            in1.putExtra("image" + (images.indexOf(img) + 1), img)
        }
        startActivity(in1)
    }

    private fun writeBitmapToFile(bitmap: Bitmap): String {
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

    private fun copyAssets() {
        val assetManager = assets

        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = assetManager.open("eng.traineddata")
            var outDir =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.absolutePath + "/tesseract"
            val dir = File(outDir)
            try {
                if (dir.mkdir()) {
                    println("Directory created")
                } else {
                    println("Directory is not created")
                }
                outDir += "/tessdata"
                val dir1 = File(outDir)
                if (dir1.mkdir()) {
                    println("Directory created")
                } else {
                    println("Directory is not created")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }


            val outFile = File(outDir, "eng.traineddata")
            out = FileOutputStream(outFile)
            copyFile(`in`, out)
            `in`.close()
            `in` = null
            out.flush()
            out.close()
            out = null
        } catch (e: IOException) {
            Log.e("tag", "Failed to copy asset file: eng.traineddata", e)
        }

    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while (`in`.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
    }
}
