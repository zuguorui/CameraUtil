package com.zu.camerautil.bean

import android.util.Size

class SizeParam: SelectionParam<Size>(CameraParamID.SIZE) {

    override val name: String
        get() = "分辨率"
    override val isModal: Boolean
        get() = false
    override val currentValue: String
        get() = current?.let{
            "${it.width}x${it.height}"
        } ?: "N"
    override val currentMode: String
        get() = throw UnsupportedOperationException("unsupported")
    override fun valueToUiElement(t: Size): AdjustUiElement {
        val str = "${t.width}x${t.height}"
        return AdjustUiElement(str, values.indexOf(t))
    }
}