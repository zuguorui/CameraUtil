package com.zu.camerautil

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityCameraInfoBinding
import com.zu.camerautil.databinding.ItemCameraSubInfoBinding
import com.zu.camerautil.util.getImageFormatName
import java.lang.StringBuilder

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
        addItem("支持硬件级别", cameraInfo.level.toString())

        binding.tvId.text = "${cameraInfo.cameraID}"
        val facingStr = when (cameraInfo.lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
            CameraCharacteristics.LENS_FACING_BACK -> "后置"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置"
            else -> "未知"
        }
        addItem("位置", facingStr)

        val isLogicalStr = if (cameraInfo.isLogical) "逻辑摄像头" else "物理摄像头"
        addItem("类型", isLogicalStr)

        if (cameraInfo.isLogical) {
            addItem("包含的物理摄像头", cameraInfo.logicalPhysicalIDs.toFormattedString())
        } else {
            cameraInfo.logicalID?.let {
                addItem("所属的逻辑摄像头", it)
            }
        }

        addItem("通过cameraIdList呈现", "${cameraInfo.isPresentByCameraManager}")

        val physicalSizeStr = with(cameraInfo.physicalSize) {
            "${width}mm * ${height}mm"
        }
        addItem("物理尺寸", physicalSizeStr)

        val pixelSizeStr = with(cameraInfo.pixelSize) {
            "$width * $height"
        }
        addItem("像素尺寸", pixelSizeStr)

        val activePixelSizeStr = with(cameraInfo.activeArraySize) {
            "${width()} * ${height()}"
        }

        addItem("可用像素尺寸", activePixelSizeStr)

        val zoomRangeStr = if (Build.VERSION.SDK_INT < 30) {
            "系统版本不支持"
        } else {
            cameraInfo.zoomRange?.let {
                "[${it.lower}, ${it.upper}]"
            } ?: "未查询到"
        }

        addItem("zoom范围", zoomRangeStr)

        val maxCropStr = String.format("%.2f", cameraInfo.maxDigitalZoom)
        addItem("最大crop倍数", maxCropStr)

        val exposureRangeStr = "${cameraInfo.exposureRange}"
        addItem("曝光时间范围", exposureRangeStr)

        val isoRangeStr = "${cameraInfo.isoRange}"
        addItem("ISO范围", isoRangeStr)

        val formatStr = kotlin.run {
            val sb = StringBuilder()
            for (i in cameraInfo.outputFormat.indices) {
                sb.append(getImageFormatName(cameraInfo.outputFormat[i]))
                if (i < cameraInfo.outputFormat.size - 1) {
                    sb.append("\n")
                }
            }
            sb.toString()
        }
        addItem("输出格式", formatStr)

        val regularFPSStr = cameraInfo.fpsRange.toFormattedString()
        addItem("支持的普通FPS", regularFPSStr)


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