package com.zu.camerautil

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.zu.camerautil.databinding.ActivitySettingBinding

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initData()
        initViews()
    }

    private fun initData() {
        when (Settings.openCameraMethod) {
            OpenCameraMethod.IN_CONFIGURATION -> binding.rbOpenCameraInConfig.isChecked = true
            else -> binding.rbOpenCameraDirectly.isChecked = true
        }

        binding.cbHighSpeedPreviewExtraSurface.isChecked = Settings.highSpeedPreviewExtraSurface
    }

    private fun initViews() {
        binding.rgOpenCameraMethod.setOnCheckedChangeListener { _, checkedId ->
            Settings.openCameraMethod = when (checkedId) {
                R.id.rb_open_camera_in_config -> OpenCameraMethod.IN_CONFIGURATION
                else -> OpenCameraMethod.DIRECTLY
            }
        }
        binding.cbHighSpeedPreviewExtraSurface.setOnCheckedChangeListener { _, isChecked ->
            Settings.highSpeedPreviewExtraSurface = isChecked
        }
    }
}