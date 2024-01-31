package com.zu.camerautil.bean

import android.graphics.Color

interface IAdjustParam<T> {
    fun valueToUiElement(t: T): AdjustUiElement
}

data class AdjustUiElement(
    val text: String,
    val id: Int,
    val textColor: Int = Color.BLACK,
    val background: Int = Color.WHITE
)