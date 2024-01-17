package com.zu.camerautil.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.util.Range
import kotlin.math.roundToInt

/**
 * @author zuguorui
 * @date 2024/1/9
 * @description
 */

object WbUtil {

    var phoneColorSpaceTransform: ColorSpaceTransform? = null

    private fun combineArrayByRatio(fromArray: FloatArray, toArray: FloatArray, ratio: Float): FloatArray {
        val result = FloatArray(3)
        for (i in 0 until 3) {
            val from = fromArray[i]
            val to = toArray[i]
            result[i] = if (from > to) from - Math.abs(from - to) * ratio else from + Math.abs(from - to) * ratio
        }
        return result
    }

    private fun transformToRange(srcMin: Float, srcMax: Float, dstMin: Float, dstMax: Float, src: Float): Float {
        return dstMin + (dstMax - dstMin) * ((src - srcMin) / (srcMax - srcMin))
    }


    fun computeRggbChannelVector(temp: Int, tint: Int): RggbChannelVector {
        val tempF = (temp.toFloat() - TEMP_RANGE.lower) / (TEMP_RANGE.upper - TEMP_RANGE.lower)
        val tintF = (tint.toFloat() - TINT_RANGE.lower) / (TINT_RANGE.upper - TINT_RANGE.lower)
        val tempArray = combineArrayByRatio(BLUE_GREEN, RED_GREEN, tempF)
        val tintArray = combineArrayByRatio(GREEN, WHITE, tintF)
        val combined = combineArrayByRatio(tempArray, tintArray, 0.5f)

        val rGain = transformToRange(0.0f, 255.0f, 1.0f, 3.0f, combined[0])
        val gGain = transformToRange(0.0f, 255.0f, 1.0f, 3.0f, combined[1]) / 2
        val bGain = transformToRange(0.0f, 255.0f, 1.0f, 3.0f, combined[2])

        return RggbChannelVector(rGain, gGain, gGain, bGain)
    }

    fun computeTempAndTint(vector: RggbChannelVector): Pair<Int, Int> {
        var rGain = vector.red
        var bGain = vector.blue

        rGain = transformToRange(1.0f, 3.0f, 0.0f, 255.0f, rGain)
        bGain = transformToRange(1.0f, 3.0f, 0.0f, 255.0f, bGain)

        val tint = (rGain + bGain - 127.5f) / 255
        val temp = (rGain - bGain + 127.5f) / 255

        val tempI = ((1 - temp) * TEMP_RANGE.lower + temp * TEMP_RANGE.upper).roundToInt()
        val tintI = ((1 - tint) * TINT_RANGE.lower + tint * TINT_RANGE.upper).roundToInt()
        return Pair(tempI, tintI)
    }

    fun computeRggbChannelVector(temp: Int): RggbChannelVector {
        return computeRggbChannelVector(temp, 0)
    }

    fun computeTemp(vector: RggbChannelVector): Int {
        var rGain = vector.red
        rGain = transformToRange(1.0f, 3.0f, 0.0f, 255.0f, rGain)

        var bGain = vector.blue
        bGain = transformToRange(1.0f, 3.0f, 0.0f, 255.0f, bGain)

        val tempF = (rGain - bGain + 127.5f) / 255

        return ((1 - tempF) * TEMP_RANGE.lower + tempF * TEMP_RANGE.upper).roundToInt()
    }



    fun getWbModeName(wbMode: Int): String? = WB_MODE_NAME_MAP[wbMode]

    private val WB_MODE_NAME_MAP = HashMap<Int, String>().apply {
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
    val CTS_COLOR = ColorSpaceTransform(MATRIX)

    private val GREEN = floatArrayOf(0.0f, 127.5f, 0.0f)
    private val WHITE = floatArrayOf(255.0f, 127.5f, 255.0f)
    private val RED_GREEN = floatArrayOf(255.0f, 127.5f, 0.0f)
    private val BLUE_GREEN = floatArrayOf(0.0f, 127.5f, 255.0f)

    val TEMP_RANGE = Range<Int>(2000, 8000)
    val TINT_RANGE = Range<Int>(-50, 50)

}



