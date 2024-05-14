package com.zu.camerautil.bean

import kotlin.math.roundToInt

/**
 * @author zuguorui
 * @date 2024/5/11
 * @description
 */
class TintParam: RangeParam<Int>(CameraParamID.TINT) {
    override val name: String
        get() = "色调"
    override val isModal: Boolean
        get() = false
    override val valueName: String
        get() = value?.toString() ?: "N/A"
    override val modeName: String
        get() = throw UnsupportedOperationException("tint is not modal")
    override val isDiscrete: Boolean
        get() = false
    override val uiStep: Float
        get() = throw UnsupportedOperationException("tint is not discrete")

    override var value: Int? = null
        set(value) {
            var finalValue = if (value == null) {
                null
            } else if (max != null && max!! < value) {
                max!!
            } else if (min != null && min!! > value) {
                min!!
            } else {
                value
            }
            val diff = finalValue != field
            field = finalValue
            if (diff) {
                notifyValueChanged()
            }
        }

    override fun uiValueToValue(uiValue: Float): Int {
        return uiValue.roundToInt()
    }

    override fun valueToUiValue(value: Int): Float {
        return value.toFloat()
    }
}