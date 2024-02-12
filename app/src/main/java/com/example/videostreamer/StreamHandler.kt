package com.example.videostreamer
import android.Manifest
import android.app.Activity
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.camera.core.CameraSelector // Select back or front
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import android.view.SurfaceView
import androidx.camera.core.Preview.SurfaceProvider

class StreamHandler (var tag : String = "MCT", var context:Context, var ViewCompo: SurfaceView) {

    private var cameraPermissionGranted = false
    companion object{
        val CAMERA_PERMISSION_REQUEST_CODE: Int = 100;
    }
    init{
        val CameraPermisson = Manifest.permission.CAMERA

//        Prepare Camera Permissions
        if (ContextCompat.checkSelfPermission(context, CameraPermisson) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE)
        }else{
            cameraPermissionGranted = true
            Log.d(tag, "Already Granted")

        }



    }

    fun onRequestPermissions(requestCode: Int, permissions: Array<out String>, grantResults: IntArray){

        Log.d(tag, "$permissions")
        Log.d(tag, "$grantResults")
        Log.d(tag, "$requestCode")

    }



}

