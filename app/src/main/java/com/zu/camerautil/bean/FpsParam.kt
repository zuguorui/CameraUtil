package com.zu.camerautil.bean

class FpsParam: SelectionParam<FPS>(CameraParamID.FPS) {

    override val name: String
        get() = "FPS"
    override val isModal: Boolean
        get() = true
    override val valueName: String
        get() = value?.toString() ?: "N"
    override val modeName: String
        get() = value?.let {
            if (it.type == FPS.Type.HIGH_SPEED) {
                "H"
            } else {
                "N"
            }
        } ?: "N"
}