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

    fun imageProxyToByteArray(image: ImageProxy): ByteArray {
        val yuvBytes = ByteArray(image.width * (image.height + image.height / 2))
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        yPlane.get(yuvBytes, 0, image.width * image.height)

        val chromaRowStride = image.planes[1].rowStride
        val chromaRowPadding = chromaRowStride - image.width / 2

        var offset = image.width * image.height
        if (chromaRowPadding == 0) {

            uPlane.get(yuvBytes, offset, image.width * image.height / 4)
            offset += image.width * image.height / 4
            vPlane.get(yuvBytes, offset, image.width * image.height / 4)
        } else {
            for (i in 0 until image.height / 2) {
                uPlane.get(yuvBytes, offset, image.width / 2)
                offset += image.width / 2
                if (i < image.height / 2 - 2) {
                    uPlane.position(uPlane.position() + chromaRowPadding)
                }
            }
            for (i in 0 until image.height / 2) {
                vPlane.get(yuvBytes, offset, image.width / 2)
                offset += image.width / 2
                if (i < image.height / 2 - 1) {
                    vPlane.position(vPlane.position() + chromaRowPadding)
                }
            }
        }

        return yuvBytes
    }


    fun sendStream(data : String){

        // server url
        var URL = "http://192.168.1.6:5000/send"
        var client = OkHttpClient()


        // req body
//        val yuvDataString = android.util.Base64.encodeToString(image, android.util.Base64.DEFAULT)


//        val file = File("{${image}}")



        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("test", "1")
            .build()

        val request = Request.Builder()
            .url(URL)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        // Create the request
        val req = Request.Builder()
            .url(URL)
            .post(body)
            .build()



        // call the request
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // Handle failure
                e.printStackTrace()
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

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                val image = imageProxy
                Log.d(TAG, "${image.width}")
                Log.d(TAG, "${image.height}")
                Log.d(TAG, "${image.format}")
                Log.d(TAG, "${image.imageInfo}")
                Log.d(TAG, "${image.image}")

                val img: Image = image!!.image!!

                val byteArrayOutputStream = ByteArrayOutputStream()
                image.use { img ->
                    val byteBuffer = img.planes[0].buffer
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    byteArrayOutputStream.write(bytes)
                }
                val imageByteArray = byteArrayOutputStream.toByteArray()

                Log.d(TAG, "$imageByteArray")

                sendStream("1")
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




