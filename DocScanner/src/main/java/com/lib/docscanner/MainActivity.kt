/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package com.lib.docscanner

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lib.docscanner.camera_module.extensions.outputDirectory
import com.lib.docscanner.camera_module.extensions.toByteArray
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.examples.ocr.MLExecutionViewModel
import org.tensorflow.lite.examples.ocr.ModelExecutionResult
import org.tensorflow.lite.examples.ocr.OCRModelExecutor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    //private val tfImageName = "tensorflow.jpg"
    // private val androidImageName = " ugandaId-2.jpg"//"android.jpg"
    // private val chromeImageName = "ugandaId-1.png"//"chrome.jpg"
    private lateinit var viewModel: MLExecutionViewModel
    private lateinit var resultImageView: ImageView
    private lateinit var tfImageView: ImageView
    private lateinit var androidImageView: ImageView
    private lateinit var chromeImageView: ImageView
    private lateinit var chipsGroup: ChipGroup
    private lateinit var runButton: Button
    private lateinit var textPromptTextView: TextView

    private var useGPU = false

    //private var selectedImageName = "tensorflow.jpg"
    private var ocrModel: OCRModelExecutor? = null
    private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()
    private val mutex = Mutex()
    private lateinit var selectedImage: Bitmap
    private lateinit var bmp1: Bitmap
    private var bmp2: Bitmap? = null
    private var image1_path: String = ""
    private var image2_path: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tfe_is_activity_main)

        image1_path = intent.getStringExtra("image1")!!
        if (intent.hasExtra("image2"))
            image2_path = intent.getStringExtra("image2")!!
        //val byteArray1 = intent.getByteArrayExtra("image1")
        bmp1 = BitmapFactory.decodeFile(image1_path)
        //bmp1 = BitmapFactory.decodeByteArray(byteArray1, 0, byteArray1!!.size)

        //val byteArray2 = intent.getByteArrayExtra("image2")
        if (!image2_path.isNullOrEmpty())
            bmp2 = BitmapFactory.decodeFile(image2_path)
        //bmp2 = BitmapFactory.decodeByteArray(byteArray2, 0, byteArray2!!.size)

        // val toolbar: Toolbar = findViewById(R.id.toolbar)
        ///setSupportActionBar(toolbar)
        //supportActionBar?.setDisplayShowTitleEnabled(false)

        tfImageView = findViewById(R.id.tf_imageview)
        androidImageView = findViewById(R.id.android_imageview)
        chromeImageView = findViewById(R.id.chrome_imageview)

        val candidateImageViews = arrayOf<ImageView>(tfImageView, androidImageView, chromeImageView)

        val assetManager = assets
        try {
            //val tfInputStream: InputStream = assetManager.open(tfImageName)
            // val tfBitmap = BitmapFactory.decodeStream(tfInputStream)
            tfImageView.setImageBitmap(bmp1)
            if (bmp2 != null)
                androidImageView.setImageBitmap(bmp2)
            selectedImage = bmp1;
            /*val androidInputStream: InputStream = assetManager.open(androidImageName)
            val androidBitmap = BitmapFactory.decodeStream(androidInputStream)

            val chromeInputStream: InputStream = assetManager.open(chromeImageName)
            val chromeBitmap = BitmapFactory.decodeStream(chromeInputStream)
            chromeImageView.setImageBitmap(chromeBitmap)*/
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open a test image")
        }

        for (iv in candidateImageViews) {
            setInputImageViewListener(iv)
        }

        resultImageView = findViewById(R.id.result_imageview)
        chipsGroup = findViewById(R.id.chips_group)
        textPromptTextView = findViewById(R.id.text_prompt)
        val useGpuSwitch: Switch = findViewById(R.id.switch_use_gpu)

        viewModel = AndroidViewModelFactory(application).create(MLExecutionViewModel::class.java)
        viewModel.resultingBitmap.observe(
            this,
            Observer { resultImage ->
                if (resultImage != null) {
                    updateUIWithResults(resultImage)
                }
                enableControls(true)
            }
        )

        mainScope.async(inferenceThread) { createModelExecutor(useGPU) }

        useGpuSwitch.setOnCheckedChangeListener { _, isChecked ->
            useGPU = isChecked
            mainScope.async(inferenceThread) { createModelExecutor(useGPU) }
        }

        runButton = findViewById(R.id.rerun_button)
        runButton.setOnClickListener {
            enableControls(false)

            mainScope.async(inferenceThread) {
                mutex.withLock {
                     if (ocrModel != null) {
                         viewModel.onApplyModel(
                             baseContext,
                             selectedImage,
                             ocrModel,
                             inferenceThread
                         )
                     } else {
                         Log.d(
                             TAG,
                             "Skipping running OCR since the ocrModel has not been properly initialized ..."
                         )
                     }
                    //detectText()
                }
            }
        }

        setChipsToLogView(HashMap<String, Int>())
        enableControls(true)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setInputImageViewListener(iv: ImageView) {
        iv.setOnTouchListener(
            object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent?): Boolean {
                    if (v.equals(tfImageView)) {
                        selectedImage = bmp1
                        textPromptTextView.setText(getResources().getString(R.string.tfe_using_first_image))
                    } else if (v.equals(androidImageView)) {
                        if (bmp2 != null) {
                            selectedImage = bmp2!!
                            textPromptTextView.setText(getResources().getString(R.string.tfe_using_second_image))
                        }
                    }/* else if (v.equals(chromeImageView)) {
            selectedImageName = chromeImageName
            textPromptTextView.setText(getResources().getString(R.string.tfe_using_third_image))
          }*/
                    return false
                }
            }
        )
    }

    private suspend fun createModelExecutor(useGPU: Boolean) {
        mutex.withLock {
            if (ocrModel != null) {
                ocrModel!!.close()
                ocrModel = null
            }
            try {
                ocrModel = OCRModelExecutor(this, useGPU)
            } catch (e: Exception) {
                Log.e(TAG, "Fail to create OCRModelExecutor: ${e.message}")
                val logText: TextView = findViewById(R.id.log_view)
                logText.text = e.message
            }
        }
    }

    private fun setChipsToLogView(itemsFound: Map<String, Int>) {
        chipsGroup.removeAllViews()

        for ((word, color) in itemsFound) {
            val chip = Chip(this)
            chip.text = word
            chip.chipBackgroundColor = getColorStateListForChip(color)
            chip.isClickable = false
            chipsGroup.addView(chip)
        }
        val labelsFoundTextView: TextView = findViewById(R.id.tfe_is_labels_found)
        if (chipsGroup.childCount == 0) {
            labelsFoundTextView.text = getString(R.string.tfe_ocr_no_text_found)
        } else {
            labelsFoundTextView.text = getString(R.string.tfe_ocr_texts_found)
        }
        chipsGroup.parent.requestLayout()
    }

    private fun getColorStateListForChip(color: Int): ColorStateList {
        val states =
            arrayOf(
                intArrayOf(android.R.attr.state_enabled), // enabled
                intArrayOf(android.R.attr.state_pressed) // pressed
            )

        val colors = intArrayOf(color, color)
        return ColorStateList(states, colors)
    }

    private fun setImageView(imageView: ImageView, image: Bitmap) {
        Glide.with(baseContext).load(image).override(250, 250).fitCenter().into(imageView)
    }

    private fun updateUIWithResults(modelExecutionResult: ModelExecutionResult) {
        setImageView(resultImageView, modelExecutionResult.bitmapResult)
        val logText: TextView = findViewById(R.id.log_view)
        logText.text = modelExecutionResult.executionLog

        setChipsToLogView(modelExecutionResult.itemsFound)
        enableControls(true)
    }

    private fun enableControls(enable: Boolean) {
        runButton.isEnabled = enable
    }

    fun removeBackground(bitmap: Bitmap?): Bitmap? {
        //GrabCut part
        var bitmap = bitmap
        val img = Mat()
        Utils.bitmapToMat(bitmap, img)
        val r = img.rows()
        val c = img.cols()
        val p1 = Point((c / 100).toDouble(), (r / 100).toDouble())
        val p2 = Point((c - c / 100).toDouble(), (r - r / 100).toDouble())
        val rect = Rect(p1, p2)
        val mask = Mat()
        val fgdModel = Mat()
        val bgdModel = Mat()
        val imgC3 = Mat()
        Imgproc.cvtColor(img, imgC3, Imgproc.COLOR_RGBA2RGB)
        Imgproc.grabCut(imgC3, mask, rect, bgdModel, fgdModel, 4, Imgproc.GC_INIT_WITH_RECT)
        val source = Mat(1, 1, CvType.CV_8U, Scalar(3.0))
        Core.compare(mask, source /* GC_PR_FGD */, mask, Core.CMP_EQ)

        //This is important. You must use Scalar(255,255, 255,255), not Scalar(255,255,255)
        val foreground = Mat(img.size(), CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0, 255.0))
        img.copyTo(foreground, mask)

        //  convert matrix to output bitmap
        bitmap = Bitmap.createBitmap(
            foreground.size().width.toInt(), foreground.size().height.toInt(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(foreground, bitmap)
        //findEdges(bitmap)
        return bitmap
    }

    fun convertToGrayScale(bitmap: Bitmap): Bitmap {
        /*val image1: Bitmap

        ///////////////transform back to Mat to be able to get Canny images//////////////////

        ///////////////transform back to Mat to be able to get Canny images//////////////////
        val img1 = Mat()
        Utils.bitmapToMat(bitmap, img1)

        //mat gray img1 holder

        //mat gray img1 holder
        val imageGray1 = Mat()

        //mat canny image

        //mat canny image
        val imageCny1 = Mat()

        //mat canny image

        //mat canny image
        val imageCny2 = Mat()


        //Convert img1 into gray image
        Imgproc.cvtColor(img1, imageGray1, Imgproc.COLOR_BGR2GRAY)

        //Canny Edge Detection

        //Canny Edge Detection
        Imgproc.Canny(imageGray1, imageCny1, 10.0, 100.0, 3, true)

        image1 = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(imageCny1, image1)

        return image1*/
        val tmp = Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1)
        Utils.bitmapToMat(bitmap, tmp)
        Imgproc.cvtColor(tmp, tmp, Imgproc.COLOR_RGB2GRAY)

        Imgproc.GaussianBlur(tmp, tmp, Size(3.0, 3.0), 0.0)
        Imgproc.adaptiveThreshold(
            tmp,
            tmp,
            255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY,
            5,
            4.0
        )
        tmp.convertTo(tmp, -1, 1.0, 50.0)
        Utils.matToBitmap(tmp, bitmap)
        writeBitmapToFile(bitmap)
        return bitmap
    }

    private fun writeBitmapToFile(bitmap: Bitmap): String {
        val file = File(outputDirectory, "${UUID.randomUUID()}.jpg")
        val outputStream = FileOutputStream(file)
        outputStream.write(bitmap.toByteArray())
        outputStream.close()

        return file.absolutePath
    }

   /* private fun detectText() {
        val tess = TessBaseAPI()

        val dataPath =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tesseract").absolutePath

        try {
            tess.init(dataPath, "eng")


            tess.setImage(selectedImage)
            val text = tess.utF8Text
            val logText: TextView = findViewById(R.id.log_view)
            val labelsFoundTextView: TextView = findViewById(R.id.tfe_is_labels_found)
            runOnUiThread {
                logText.text = text
                labelsFoundTextView.text = getString(R.string.tfe_ocr_texts_found)
                enableControls(true)
            }

            tess.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }*/


}
