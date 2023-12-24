package com.zu.camerautil.bean

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.Size
import android.util.SizeF
import kotlin.math.floor

/**
 * @author zuguorui
 * @date 2023/11/9
 * @description
 */

typealias FPSList = ArrayList<Int>
typealias SizeList = ArrayList<Size>


open class CameraInfoWrapper(
    val cameraID: String,
    val characteristics: CameraCharacteristics
) {
    val level: CameraLevel = CameraLevel.valueOf(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!)

    val lensFacing: Int = characteristics.get(CameraCharacteristics.LENS_FACING)!!

    val physicalSize: SizeF = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!

    val focalArray: FloatArray = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!

    val pixelSize: Size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!

    val activeArraySize: Rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!

    val maxDigitalZoom: Float = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!

    val minDigitalCropSize: Size = Size(
        floor(activeArraySize.width() / maxDigitalZoom).toInt(),
        floor(activeArraySize.height() / maxDigitalZoom).toInt())

    val zoomRange: Range<Float>? = if (Build.VERSION.SDK_INT >= 30) {
        characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
    } else {
        null
    }

    val isoRange: Range<Int>? = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

    // 曝光时间，纳秒
    val exposureRange: Range<Long>? = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

    var isPresentByCameraManager = true

    //计算 水平 和 竖直 视野 角度。标准镜头 视角约50度左右
    //计算公式：根据 镜头物理尺寸：
    // 水平FOV = 2 atan(0.5 width(sensor width) / focal(mm))，
    val horizontalFOV by lazy {
        val maxFocalLength = with(focalArray) { get(size - 1) }
        2 * Math.atan((physicalSize!!.width / (maxFocalLength * 2)).toDouble())
        characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
    }

    // 垂直FOV = 2 atan(0.5 height(sensor heght) / focal(mm))
    val verticalFOV by lazy {
        val maxFocalLength = with(focalArray) {get(size - 1)}
        2 * Math.atan((physicalSize.height / (maxFocalLength * 2)).toDouble())
    }

    val isLogical by lazy {
        var result = false
        val capabilities = characteristics.get(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        )!!
        for (capability in capabilities) {
            if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA == capability) {
                result = true
                break
            }
        }
        result
    }

    // 如果镜头时逻辑镜头，那该值就是组成它的物理镜头的ID。
    val logicalPhysicalIDs by lazy {
        val result = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 28) {
            if (isLogical) {
                characteristics.physicalCameraIds.forEach { id ->
                    result.add(id)
                }
            }
        }
        result
    }

    // 如果一个物理镜头属于某个逻辑镜头，那该值就是逻辑镜头ID
    var logicalID: String? = null

    val fpsRange: ArrayList<Int> by lazy {
        val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
        val rangeSet = HashSet<Int>()
        for (range in ranges) {
            rangeSet.add(range.lower)
            rangeSet.add(range.upper)
        }
        val result = ArrayList<Int>().apply {
            addAll(rangeSet)
            sort()
        }
        result
    }

    val outputFormat = ArrayList<Int>()

    val regularFpsSize = HashMap<Int, FpsSizeMap>()

    val highSpeedFpsSize = HashMap<Int, FpsSizeMap>()

    init {
        val configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        outputFormat.addAll(configurationMap.outputFormats.asIterable())



    }

    private fun queryHighSpeedInfo(format: Int) {
        val configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

        val highSpeedMap = FpsSizeMap()
        val highSpeedSizes = configurationMap.highSpeedVideoSizes
        for (size in highSpeedSizes) {
            val highSpeedFps = configurationMap.getHighSpeedVideoFpsRangesFor(size)
        }
    }



    override fun toString(): String {

        val facingStr = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_BACK -> "back"
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
            else -> "unknown"
        }



        val str = """
            camera: $cameraID {
                lensFacing: $facingStr
                isLogical: $isLogical
                logicalPhysicalIDs: ${if (isLogical) logicalPhysicalIDs.toString() else "None"}
                physicalSize: $physicalSize
                focalArray: ${focalArray.contentToString()}
                pixelSizeArray: $pixelSize
                activePixelArray: $activeArraySize
                zoomRange: $zoomRange
                maxDigitalZoom: $maxDigitalZoom
                minDigitalCropSize: $minDigitalCropSize
                horizontalFOV: $horizontalFOV
                verticalFOV: $verticalFOV
            }
        """.trimIndent()
        val str1 = """
            camera: $cameraID {
                facing: $facingStr
                isLogical: $isLogical
                focalArray: ${focalArray.contentToString()}
                ISO range: $isoRange
                exposure range: $exposureRange
            }
        """.trimIndent()
        return str1
    }
}

