package com.zu.camerautil.bean

import kotlin.math.roundToInt

/**
 * @author zuguorui
 * @date 2024/5/11
 * @description
 */
class TempParam: RangeParam<Int>(CameraParamID.TEMP) {
    override val name: String
        get() = "色温"
    override val isModal: Boolean
        get() = false
    override val valueName: String
        get() = value?.toString() ?: "N/A"
    override val modeName: String
        get() = throw UnsupportedOperationException("temp is not modal")
    override val isDiscrete: Boolean
        get() = false
    override val uiStep: Float
        get() = throw UnsupportedOperationException("temp is not step value")

    override fun uiValueToValue(uiValue: Float): Int {
        return uiValue.roundToInt()
    }

    override fun valueToUiValue(value: Int): Float {
        return value.toFloat()
    }
}