package com.zu.camerautil.bean

import android.util.Size

class SizeParam: SelectionParam<Size>(CameraParamID.SIZE) {

    override val name: String
        get() = "分辨率"
    override val isModal: Boolean
        get() = false
    override val valueName: String
        get() = value?.let{
            "${it.width}x${it.height}"
        } ?: "N"
    override val modeName: String
        get() = throw UnsupportedOperationException("unsupported")
    override fun valueToSelectionElement(t: Size): UiElement {
        val str = "${t.width}x${t.height}"
        return UiElement(str, values.indexOf(t))
    }
}