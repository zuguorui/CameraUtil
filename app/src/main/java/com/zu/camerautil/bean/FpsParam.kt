package com.zu.camerautil.bean

class FpsParam: SelectionParam<FPS>(CameraParamID.FPS) {

    override val name: String
        get() = "FPS"
    override val isModal: Boolean
        get() = true
    override val currentValue: String
        get() = current?.toString() ?: "N"
    override val currentMode: String
        get() = current?.let {
            if (it.type == FPS.Type.HIGH_SPEED) {
                "H"
            } else {
                "N"
            }
        } ?: "N"
    override fun valueToUiElement(t: FPS): AdjustUiElement {
        return AdjustUiElement(t.toString(), values.indexOf(t))
    }


}