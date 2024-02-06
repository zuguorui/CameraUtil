package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import com.zu.camerautil.R
import com.zu.camerautil.bean.RangeListener
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.bean.ValueListener
import com.zu.camerautil.databinding.ParamRangeBinding
import timber.log.Timber

class RangeParamPopupWindow: EasyLayoutPopupWindow {

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
                removeRangeListener(rangeListener)
                removeValueListener(valueListener)
            }
            value?.run {
                addRangeListener(rangeListener)
                addValueListener(valueListener)
            }
            field = value

        }

    constructor(context: Context): super(context) {
        layoutInflater = LayoutInflater.from(context)
        binding = ParamRangeBinding.inflate(layoutInflater)
        binding.root.layoutParams = ViewGroup.LayoutParams(5000, ViewGroup.LayoutParams.WRAP_CONTENT)
        contentView = binding.root

        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.WHITE))
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