package com.zu.camerautil.bean

import androidx.annotation.FloatRange

open class RangeParam<T: Number>(val id: CameraParamID): ICameraParam<T>{
    var max: T? = null
    var min: T? = null
    var current: T? = null

    override fun valueToUiElement(t: T): UiElement {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return "$id: range = [$min, $max], current = $current"
    }
}