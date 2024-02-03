package com.zu.camerautil.view

import android.content.Context
import android.view.LayoutInflater
import android.widget.PopupWindow
import com.zu.camerautil.bean.RangeListener
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.bean.ValueListener
import com.zu.camerautil.databinding.ParamRangeBinding

class RangeParamPopupWindow: PopupWindow {

    private val context: Context

    private val layoutInflater: LayoutInflater

    private lateinit var binding: ParamRangeBinding

    private val valueListener: ValueListener<Any> = { value ->
        value?.let {
            val param = param ?: return@let
            val uiValue = param.valueToUiValue(it)
            val uiName = param.valueToUiName(it)
            binding.tvValue.text = uiName
            binding.slider.value = uiValue
        }
    }

    private val rangeListener: RangeListener<Any> = { min, max ->
        refreshView()
    }

    var param: RangeParam<Any>? = null
        set(value) {
            if (field == value) {
                return
            }
            field?.run {
                removeOnRangeChangedListener(rangeListener)
                removeValueListener(valueListener)
            }
            value?.run {
                addOnRangeChangedListener(rangeListener)
                addValueListener(valueListener)
            }
            field = value

        }

    constructor(context: Context): super(context) {
        this.context = context
        layoutInflater = LayoutInflater.from(context)
        binding = ParamRangeBinding.inflate(layoutInflater)
        refreshView()
    }

    private fun refreshView() {
        param?.let {
            val min = it.min ?: return@let
            val max = it.max ?: return@let
            val value = it.value ?: return@let

            val uiMin = it.valueToUiValue(min)
            val minName = it.valueToUiName(min)

            val uiMax = it.valueToUiValue(max)
            val maxName = it.valueToUiName(max)

            val uiValue = it.valueToUiValue(value)
            val valueName = it.valueToUiName(value)

            binding.tvMin.text = minName
            binding.tvMax.text = maxName
            binding.tvValue.text = valueName

            binding.slider.valueFrom = uiMin
            binding.slider.valueTo = uiMax
            binding.slider.value = uiValue
            if (it.isDiscrete) {
                binding.slider.stepSize = it.uiStep
            }

        }
    }

}