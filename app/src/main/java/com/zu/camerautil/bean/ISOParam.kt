package com.zu.camerautil.bean

import kotlin.math.roundToInt

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
        return uiValue.roundToInt()
    }

    override fun valueToUiValue(value: Int): Float {
        return value.toFloat()
    }
}