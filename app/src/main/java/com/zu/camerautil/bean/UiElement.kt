package com.zu.camerautil.bean

import android.graphics.Color

data class UiElement(
    val text: String,
    val id: Int,
    val textColor: Int = Color.BLACK,
    val background: Int = Color.WHITE
)
