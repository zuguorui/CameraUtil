package com.zu.camerautil.bean

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ISO在低端比较敏感，因此使用y = 2^(0.5x) - 1来映射。最小值为[0, 0]。
 * 最大值为[8, 15]。
 * */
class ISOParam: RangeParam<Int>(CameraParamID.ISO) {

    override val name: String
        get() = "ISO"
    override val isModal: Boolean
        get() = true
    override val valueName: String
        get() = value?.toString() ?: "N/A"
    override val modeName: String
        get() = if (isAutoMode) "A" else "M"
    override val isDiscrete: Boolean
        get() = false
    override val uiStep: Float
        get() = throw UnsupportedOperationException("not supported")

    override fun uiValueToValue(uiValue: Float): Int {
        if (max == null || min == null) {
            return 0
        }
        val x = uiValue.toDouble()
        val y = 2.0.pow(0.5 * x) - 1
        val value = (y - Y_MIN) / (Y_MAX - Y_MIN) * (max!! - min!!) + min!!
        return value.roundToInt()
    }

    override fun valueToUiValue(value: Int): Float {
        if (max == null || min == null) {
            return 0f
        }
        val y = (value - min!!).toDouble() / (max!! - min!!) * (Y_MAX - Y_MIN) + Y_MIN
        val x = 2 * log2(y + 1)
        return x.toFloat()
    }

    companion object {
        private const val X_MIN = 0f
        private const val Y_MIN = 0f
        private const val X_MAX = 8f
        private const val Y_MAX = 15f
    }
}