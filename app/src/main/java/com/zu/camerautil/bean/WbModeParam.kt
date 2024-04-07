package com.zu.camerautil.bean

import android.graphics.Color
import com.zu.camerautil.camera.WbUtil

/**
 * @author zuguorui
 * @date 2024/4/7
 * @description
 */
class WbModeParam: SelectionParam<Int> (CameraParamID.WB_MODE) {
    override val name: String
        get() = "白平衡"
    override val isModal: Boolean
        get() = false
    override val valueName: String
        get() = value?.let {
            WbUtil.getWbModeName(it)
        } ?: "unknown"

    override val modeName: String
        get() = throw UnsupportedOperationException("unsupported")

    override fun valueToSelectionElement(t: Int): UiElement {
        val str = WbUtil.getWbModeName(t) ?: "unknown"
        val color = Color.BLACK
        return UiElement(str, t, color)
    }

    override fun selectionElementToValue(element: UiElement): Int {
        return element.id
    }
}