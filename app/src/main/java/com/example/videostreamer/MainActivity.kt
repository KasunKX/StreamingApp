package com.example.videostreamer
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.example.videostreamer.databinding.MainActivityBinding
import androidx.lifecycle.LifecycleOwner
import android.widget.Button
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import android.view.SurfaceView
import android.view.TextureView
import androidx.camera.core.Preview
import java.nio.ByteBuffer
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.camera.core.ImageAnalysis
import java.io.ByteArrayOutputStream
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import org.apache.commons.codec.binary.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.io.File


import okhttp3.*
import java.io.IOException
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

typealias CornersListener = () -> Unit

class MainActivity : ComponentActivity() {

    private lateinit var streamHandler: StreamHandler
    private lateinit var binding: MainActivityBinding// Replace MainLayoutBinding with your actual binding class name
    private lateinit var camView: TextureView

    companion object{
        const val TAG = "MCT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            binding = MainActivityBinding.inflate(layoutInflater)

            setContentView(binding.root)

            streamHandler = StreamHandler(context = this)


            val startButton = findViewById<Button>(R.id.generateStreamBtn)

            startButton.setOnClickListener{
                startButton.setBackgroundColor(resources.getColor(R.color.white))
                startButton.setTextColor(resources.getColor(R.color.bgBlue))

                startButton.setText("Please Wait")

                Log.d(TAG, "Please Wait")
                startCamera()

                Handler().postDelayed({
                    startButton.setBackgroundColor(resources.getColor(R.color.bgBlue))
                    startButton.setTextColor(resources.getColor(R.color.white))
                      startButton.setText("Started ! ")
                }, 1000)
            }

        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        streamHandler.onRequestPermissions(requestCode, permissions, grantResults)
    }


    fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val vuBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)
        yBuffer.get(nv21, 0, ySize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }



    fun sendStream(imageProxy : ImageProxy){

        // server url
        var URL = "http://192.168.146.180:5000/send"
        var client = OkHttpClient()

        val bitmap = imageProxy.toBitmap()

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 1, outputStream)
        val byteArray = outputStream.toByteArray()

        val mediaType = "image/png".toMediaType()
        val requestBody = byteArray.toRequestBody(mediaType)


        // Create the request
        val req = Request.Builder()
            .url(URL)
            .post(requestBody)
            .build()



        // call the request
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // Handle failure

                e.printStackTrace()

                Log.d(TAG, "Error ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                // Handle response
                if (!response.isSuccessful) {
                    Log.d(TAG, "Failed to send data: ${response.code}")
                } else {
                    Log.d(TAG,"Data sent successfully")
                }
            }
        })

    }



    @OptIn(ExperimentalGetImage::class) private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build()

            val imageCapture = ImageCapture.Builder().build()
            val cameraExecutor = Executors.newSingleThreadExecutor()

            val viewFinder: PreviewView = binding.viewFinder

            Log.d(TAG, "Declaring Image Analysis")

            val imageAnalysis = ImageAnalysis.Builder()
                // enable the following line if RGBA output is needed.
                // .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(720, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            Log.d(TAG, "Initializing Stream...")


            // ImAage Analysis ******************************************************
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                sendStream(imageProxy)


//                sendStream(bytes)
                // call the

                imageProxy.close()
            })


            viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
            preview.setSurfaceProvider(viewFinder.surfaceProvider)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

}




