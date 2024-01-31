package com.zu.camerautil.bean

import android.graphics.Color

abstract class SelectionParam<T>(val id: CameraParamID): ICameraParam<T> {
    var values = ArrayList<T>()
    var current: T? = null

    override fun toString(): String {
        return "$id: current = $current"
    }
}