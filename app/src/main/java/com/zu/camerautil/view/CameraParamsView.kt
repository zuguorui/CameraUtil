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


    private class SecParam: RangeParam<Long>(CameraParamID.SEC) {
//        override fun translateToUiValue(t: Long): String {
//
//        }
//
//        override fun translatePercentToValue(percent: Float): Long {
//
//        }

        companion object {
            private val VALUES = floatArrayOf(
                1.0f / 5,
                1.0f / 6,
                1.0f / 7,
                1.0f / 10,
                1.0f / 15,
                1.0f / 24,
                1.0f / 25,
                1.0f / 30,
                1.0f / 40,
                1.0f / 50,
                1.0f / 60,
                1.0f / 70,
                1.0f / 80,
                1.0f / 90,
                1.0f / 100,
                1.0f / 110,
                1.0f / 120,
                1.0f / 140,
                1.0f / 160,
                1.0f / 180,
                1.0f / 200,
                1.0f / 240,
                1.0f / 300,
                1.0f / 350,
                1.0f / 400,
                1.0f / 450,
                1.0f / 500,
                1.0f / 550,
                1.0f / 600,
                1.0f / 650,
                1.0f / 700,
                1.0f / 750,
                1.0f / 800,
                1.0f / 850,
                1.0f / 900,
                1.0f / 1000,
                1.0f / 1100,
                1.0f / 1200,
                1.0f / 1300,
                1.0f / 1400,
                1.0f / 1500,
                1.0f / 1600,
                1.0f / 1700,
                1.0f / 1800,
                1.0f / 1900,
                1.0f / 2000,
                1.0f / 2200,
                1.0f / 2400,
                1.0f / 2600,
                1.0f / 2800,
                1.0f / 3000,
                1.0f / 3300,
                1.0f / 3600,
                1.0f / 3900,
                1.0f / 4200,
                1.0f / 4600,
                1.0f / 5000,
                1.0f / 5500,
                1.0f / 5600
            )
        }
    }

}