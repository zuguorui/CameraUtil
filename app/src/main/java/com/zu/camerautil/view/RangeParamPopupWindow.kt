package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.zu.camerautil.bean.AutoModeListener
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.RangeListener
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.bean.ValueListener
import com.zu.camerautil.databinding.ParamRangeBinding
import timber.log.Timber

class RangeParamPopupWindow: EasyLayoutPopupWindow {

    private lateinit var id: CameraParamID

    private val layoutInflater: LayoutInflater

    private lateinit var binding: ParamRangeBinding

    private val valueListener: ValueListener<Any> = { value ->
        value?.let {
            val param = param ?: return@let
            val uiValue = param.valueToUiValue(it)
            val uiName = param.valueToUiName(it)
            binding.tvValue.text = uiName
            if (param?.isAutoMode == true) {
                binding.slider.value = uiValue
            }
        }
    }

    private val rangeListener: RangeListener<Any> = { min, max ->
        Timber.d("param $id range changed: min = $min, max = $max")
        refreshView()
    }

    private val autoModeListener: AutoModeListener = { isAuto ->
        binding.swAuto.isChecked = isAuto
    }

    override var isEnabled = true
        set(value) {
            binding.slider.isEnabled = value
            field = value
        }

    var param: RangeParam<Any>? = null
        set(value) {
            Timber.d("param $id set")
            if (field == value) {
                return
            }
            field?.run {
                removeRangeListener(rangeListener)
                removeValueListener(valueListener)
                removeAutoModeListener(autoModeListener)
            }
            value?.run {
                addRangeListener(rangeListener)
                addValueListener(valueListener)
                addAutoModeListener(autoModeListener)
            }
            field = value
            refreshView()
        }

    constructor(context: Context, id: CameraParamID): super(context) {
        this.id = id
        layoutInflater = LayoutInflater.from(context)
        binding = ParamRangeBinding.inflate(layoutInflater)
        binding.root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        contentView = binding.root

        binding.swAuto.setOnCheckedChangeListener { _, isChecked ->
            param?.isAutoMode = isChecked
            binding.slider.isEnabled = !isChecked
            if (isChecked) {
                refreshView()
            }
        }

        binding.slider.addOnChangeListener { slider, value, fromUser ->
            param?.let {
                if (it.isModal && it.isAutoMode) {
                    return@let
                }
                if (fromUser) {
                    it.value = it.uiValueToValue(value)
                }
            }
        }

        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.WHITE))
        refreshView()
    }

    private fun refreshView() {
        Timber.d("param $id refresh view")
        param?.let {
            it.min?.let { min ->
                val uiMin = it.valueToUiValue(min)
                val minName = it.valueToUiName(min)
                Timber.d("param $id min = $min, uiMin = $uiMin, minName = $minName")
                binding.tvMin.text = minName
                binding.slider.valueFrom = uiMin
            }

            it.max?.let { max ->
                val uiMax = it.valueToUiValue(max)
                val maxName = it.valueToUiName(max)
                Timber.d("param $id max = $max, uiMax = $uiMax, maxName = $maxName")
                binding.tvMax.text = maxName
                binding.slider.valueTo = uiMax
            }

            it.value?.let { value ->
                val uiValue = it.valueToUiValue(value)
                val valueName = it.valueToUiName(value)
                Timber.d("param $id value = $value, uiValue = $uiValue, valueName = $valueName")
                binding.tvValue.text = valueName
                binding.slider.value = uiValue
            }

            if (it.isModal) {
                binding.swAuto.visibility = View.VISIBLE
                binding.swAuto.isChecked = it.isAutoMode
            } else {
                binding.swAuto.visibility = View.GONE
            }

            if (it.isDiscrete) {
                binding.slider.stepSize = it.uiStep
            }

        }
    }

}