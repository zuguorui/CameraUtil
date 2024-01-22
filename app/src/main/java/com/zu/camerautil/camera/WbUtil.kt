package com.zu.camerautil.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.util.Range
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * @author zuguorui
 * @date 2024/1/9
 * @description
 */

object WbUtil {

    private val DEFAULT_CST_MATRIX = intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1)
    val DEFAULT_CST = ColorSpaceTransform(DEFAULT_CST_MATRIX)

    // 纯绿
    private val GREEN = floatArrayOf(0.0f, 127.5f, 0.0f)
    // 品红
    private val MAGENTA = floatArrayOf(255.0f, 127.5f, 255.0f)
    // 红色
    private val RED = floatArrayOf(255.0f, 127.5f, 0.0f)
    // 蓝色
    private val BLUE = floatArrayOf(0.0f, 127.5f, 255.0f)

    val TEMP_RANGE = Range<Int>(2000, 10000)
    val TINT_RANGE = Range<Int>(-50, 50)

    var previousCST: ColorSpaceTransform? = null
        set(value) {
            val diff = field != value
            field = value
            if (diff) {
                value?.let {
                    val sb = StringBuilder("transform:\n")
                    for (row in 0 until 3) {
                        for (col in 0 until 3) {
                            sb.append("${it.getElement(col, row)}")
                            if (col < 2) {
                                sb.append(", ")
                            }
                        }
                        if (row < 2) {
                            sb.append("\n")
                        }
                    }
                    Timber.d(sb.toString())
                } ?: Timber.d("transform: null")
            }
        }

    private var previousTint: Int = (TINT_RANGE.lower + TINT_RANGE.upper) / 2

    private fun combineArrayByRatio(fromArray: FloatArray, toArray: FloatArray, ratio: Float): FloatArray {
        assert(fromArray.size == toArray.size)
        val size = fromArray.size
        val result = FloatArray(size)
        for (i in 0 until size) {
            result[i] = (1 - ratio) * fromArray[i] + ratio * toArray[i]
        }
        return result
    }

    private fun transformToRange(srcMin: Float, srcMax: Float, dstMin: Float, dstMax: Float, src: Float): Float {
        return dstMin + (dstMax - dstMin) * ((src - srcMin) / (srcMax - srcMin))
    }


    fun computeRggbChannelVector(temp: Int, tint: Int): RggbChannelVector {
        val tempF = (temp.toFloat() - TEMP_RANGE.lower) / (TEMP_RANGE.upper - TEMP_RANGE.lower)
        val tintF = (tint.toFloat() - TINT_RANGE.lower) / (TINT_RANGE.upper - TINT_RANGE.lower)
        val tempArray = combineArrayByRatio(BLUE, RED, tempF)
        val tintArray = combineArrayByRatio(GREEN, MAGENTA, tintF)
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


        val temp = (rGain - bGain + 127.5f) / 255
        val tint = (rGain + bGain - 127.5f) / 255

        val tempI = ((1 - temp) * TEMP_RANGE.lower + temp * TEMP_RANGE.upper).roundToInt()
        val tintI = ((1 - tint) * TINT_RANGE.lower + tint * TINT_RANGE.upper).roundToInt()
        previousTint = tintI
        return Pair(tempI, tintI)
    }

    fun computeRggbChannelVector(temp: Int): RggbChannelVector {
        Timber.d("computeRggbChannelVector: tint = $previousTint")
        return computeRggbChannelVector(temp, previousTint)
    }

    fun computeTemp(vector: RggbChannelVector): Int {
        return computeTempAndTint(vector).first
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

}



