package com.zu.camerautil.bean

import android.graphics.Color
import com.zu.camerautil.camera.FlashUtil

/**
 * @author zuguorui
 * @date 2024/4/7
 * @description
 */
class FlashParam: SelectionParam<FlashUtil.FlushMode> (CameraParamID.FLASH_MODE) {
    override val name: String
        get() = "闪光灯"
    override val isModal: Boolean
        get() = false
    override val valueName: String
        get() = value?.let {
            FlashUtil.getFlushModeName(it)
        } ?: "unknown"
    override val modeName: String
        get() = throw UnsupportedOperationException("unsupported")

    override fun valueToSelectionElement(t: FlashUtil.FlushMode): UiElement {
        return UiElement(FlashUtil.getFlushModeName(t), t.id, Color.BLACK)
    }

    override fun selectionElementToValue(element: UiElement): FlashUtil.FlushMode {
        return FlashUtil.FlushMode.valueOf(element.id)!!
    }
}