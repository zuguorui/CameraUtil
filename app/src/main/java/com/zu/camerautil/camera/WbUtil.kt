package com.zu.camerautil.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.util.Range
import androidx.annotation.Size

/**
 * @author zuguorui
 * @date 2024/1/9
 * @description
 */

val WB_MODE_NAME_MAP = HashMap<Int, String>().apply {
    put(CameraCharacteristics.CONTROL_AWB_MODE_AUTO, "自动")
    put(CameraCharacteristics.CONTROL_AWB_MODE_OFF, "关闭")
    put(CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT, "白炽灯")
    put(CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT, "荧光灯")
    put(CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT, "暖灯")
    put(CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT, "日光")
    put(CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "阴天")
    put(CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT, "黄昏")
    put(CameraCharacteristics.CONTROL_AWB_MODE_SHADE, "阴影")
}


private val MATRIX = intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1)

//private val MATRIX = intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1)

val CTS_COLOR = ColorSpaceTransform(MATRIX)
fun getWbModeName(wbMode: Int): String? = WB_MODE_NAME_MAP[wbMode]


private val GREEN = floatArrayOf(0.0f, 127.5f, 0.0f)
private val WHITE = floatArrayOf(255.0f, 127.5f, 255.0f)
private val RED_GREEN = floatArrayOf(255.0f, 127.5f, 0.0f)
private val BLUE_GREEN = floatArrayOf(0.0f, 127.5f, 255.0f)

fun m8117(@Size(3) fArr: FloatArray, @Size(3) fArr2: FloatArray, f: Float): FloatArray {
    val fArr3 = FloatArray(3)
    val f2 = fArr[0]
    val f3 = fArr2[0]
    fArr3[0] = if (f2 > f3) f2 - Math.abs(f2 - f3) * f else f2 + Math.abs(f2 - f3) * f
    val f4 = fArr[1]
    val f5 = fArr2[1]
    fArr3[1] = if (f4 > f5) f4 - Math.abs(f4 - f5) * f else f4 + Math.abs(f4 - f5) * f
    val f6 = fArr[2]
    val f7 = fArr2[2]
    fArr3[2] = if (f6 > f7) f6 - Math.abs(f6 - f7) * f else f6 + Math.abs(f6 - f7) * f
    return fArr3
}

fun m8118(f: Float, f2: Float, f3: Float, f4: Float, f5: Float): Float {
    return f3 + (f4 - f3) * ((f5 - f) / (f2 - f))
}

fun m8119(f: Float): Float {
    return Math.max(0.0f, Math.min(1.0f, f))
}

fun m8120(range: Range<Float>, range2: Range<Float>, f: Float): Float {
    return range2.lower + (range2.upper - range2.lower) * ((f - range.lower) / (range.upper - range.lower))
}

fun computeRggbChannelVector(f: Float, f2: Float): RggbChannelVector {
    var fArr1 = m8117(m8117(BLUE_GREEN, RED_GREEN, f), m8117(GREEN, WHITE, f2), 0.5f)
    var f3 = m8118(0.0f, 255.0f, 1.0f, 3.0f, fArr1[0])
    var f4 = m8118(0.0f, 255.0f, 1.0f, 3.0f, fArr1[1]) / 2
    var f5 = m8118(0.0f, 255.0f, 1.0f, 3.0f, fArr1[2])
    return RggbChannelVector(f3, f4, f4, f5)
}

