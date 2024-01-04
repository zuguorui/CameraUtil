package com.zu.camerautil

import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.zu.camerautil.databinding.ActivityMainBinding
import com.zu.camerautil.util.NeonTest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val permissionList = listOf<String>(android.Manifest.permission.CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        checkPermission()
    }

    private fun initViews() {
        binding.btnCameraInfo.setOnClickListener {
            startActivity(CameraListActivity::class.java)
        }

        binding.btnMultiSurface.setOnClickListener {
            startActivity(MultiSurfaceActivity::class.java)
        }

        binding.btnZoom.setOnClickListener {
            startActivity(ZoomActivity::class.java)
        }

        binding.btnCrop.setOnClickListener {
            startActivity(CropActivity::class.java)
        }

        binding.btnNeonTest.setOnClickListener {
            NeonTest.doNeonTest()
        }
    }

    private fun <T> startActivity(target: Class<T>) {
        val intent = Intent(this, target)
        startActivity(intent)
    }

    private fun checkPermission() {
        var unGrantedPermissions = ArrayList<String>()
        for (permission in permissionList) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                unGrantedPermissions.add(permission)
            }
        }

        if (unGrantedPermissions.size != 0) {
            requestPermissions(unGrantedPermissions.copyToArray(), 33)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            33 -> {
                for (i in 0 until permissions.size) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "onRequestPermissionsResult: permission ${permissions[i]} not granted")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}