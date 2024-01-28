package com.zu.camerautil.bean

interface ICameraParam<T> {
    fun translateToUiValue(t: T): String {
        return t.toString()
    }
}