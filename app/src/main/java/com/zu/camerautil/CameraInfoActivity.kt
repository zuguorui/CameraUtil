package com.zu.camerautil

import android.hardware.camera2.CameraCharacteristics
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityCameraInfoBinding
import com.zu.camerautil.databinding.ItemCameraSubInfoBinding

class CameraInfoActivity : AppCompatActivity() {

    private lateinit var cameraId: String

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private val cameraInfo: CameraInfoWrapper
        get() = cameraInfoMap[cameraId]!!

    private lateinit var binding: ActivityCameraInfoBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraId = intent.getStringExtra("camera_id")!!
        initViews()
    }

    private fun initViews() {
        binding.tvId.text = "${cameraInfo.cameraID}"
        val facingStr = when (cameraInfo.lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
            CameraCharacteristics.LENS_FACING_BACK -> "后置"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置"
            else -> "未知"
        }
        addItem("位置", facingStr)

        addItem("逻辑摄像头", "${cameraInfo.isLogical}")

        if (cameraInfo.isLogical) {
            addItem("包含的物理摄像头", cameraInfo.logicalPhysicalIDs.toFormattedString())
        }
    }

    private var itemIndex = 0
    private fun addItem(name: String, content: String) {
        val itemBinding = ItemCameraSubInfoBinding.inflate(layoutInflater)
        itemBinding.tvName.text = name
        itemBinding.tvValue.text = content
        val color = if (itemIndex % 2 == 0) R.color.blue_dark else R.color.blue_light
        itemBinding.root.setBackgroundColor(getColor(color))
        binding.llItems.addView(itemBinding.root)
        itemIndex++
    }
}