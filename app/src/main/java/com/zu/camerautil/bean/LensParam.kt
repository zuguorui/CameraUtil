package com.zu.camerautil.bean

import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics

class LensParam: SelectionParam<CameraInfoWrapper>(CameraParamID.LENS) {

    override val name: String
        get() = "镜头"
    override val isModal: Boolean
        get() = false
    override val valueName: String
        get() = value?.cameraID ?: "N"
    override val modeName: String
        get() = throw UnsupportedOperationException("unsupported")
    override fun valueToSelectionElement(t: CameraInfoWrapper): UiElement {
        val facing = if (t.lensFacing == CameraCharacteristics.LENS_FACING_BACK) "back" else "front"
        val logical = if (t.isLogical) "logical" else "physical"
        val focal = if (t.focalArray.isNotEmpty()) String.format("%.1f", t.focalArray[0]) else "_"
        val str = "Camera${t.cameraID}_${facing}_${logical}_focal(${focal})"

        val color = if (t.isInCameraIdList) Color.GREEN else Color.RED
        return UiElement(str, values.indexOf(t), color)
    }


}