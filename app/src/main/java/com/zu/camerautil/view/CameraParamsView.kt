package com.zu.camerautil.view

import android.content.Context
import android.util.AttributeSet
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.databinding.ItemCameraParamBinding

class CameraParamsView: AbsCameraParamView {

    private val bindingMap = HashMap<CameraParamID, ItemCameraParamBinding>()
    private val panelMap = HashMap<CameraParamID, RangeParamPopupWindow>()
    private val paramMap = HashMap<CameraParamID, RangeParam<Number>>()

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        initParamViews()
    }

    private fun initParamViews() {

    }



}