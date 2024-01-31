package com.zu.camerautil.view

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.zu.camerautil.databinding.ParamRangeBinding

class RangeParamPopupWindow: PopupWindow {

    private val context: Context

    private val layoutInflater: LayoutInflater

    private val binding: ParamRangeBinding

    var min: Int = 0
        set(value) {
            field = value
            binding.tvMin.text = valueTranslator?.invoke(value) ?: "$value"

            if (current < value) {
                current = value
            }

            val percent = (current - value).toFloat() / (max - value)
            binding.slider.value = percent * (binding.slider.valueTo - binding.slider.valueFrom) + binding.slider.valueFrom
        }
    var max: Int = 100
        set(value) {
            field = value
            binding.tvMin.text = valueTranslator?.invoke(value) ?: "$value"
            if (current > value) {
                current = value
            }
            val percent = (current - min).toFloat() / (value - min)
            binding.slider.value = percent * (binding.slider.valueTo - binding.slider.valueFrom) + binding.slider.valueFrom
        }

    private var _current: Int = 0
        set(value) {
            field = value
            binding.tvValue.text = valueTranslator?.invoke(value) ?: "$value"
        }
    var current: Int
        get() = _current
        set(value) {
            if (_current == value) {
                return
            }
            val percent = (value - min).toFloat() / (max - min)
            binding.slider.value = percent * (binding.slider.valueTo - binding.slider.valueFrom) + binding.slider.valueFrom
        }

    var isAuto: Boolean = true
        set(value) {
            val diff = field == value
            field = value
            if (binding.swAuto.isChecked != value) {
                binding.swAuto.isChecked = value
            }
            if (diff) {
                onAutoModeChangedListener?.invoke(value)
            }
        }

    var valueTranslator: ((Int) -> String)? = null
    var onValueChangedListener: ((Int) -> Unit)? = null
    var onAutoModeChangedListener: ((Boolean) -> Unit)? = null

    constructor(context: Context): super(context) {
        this.context = context
        layoutInflater = LayoutInflater.from(context)
        binding = ParamRangeBinding.inflate(layoutInflater)
        initViews()
    }

    private fun initViews() {
        // 手动初始化一下
        min = 0
        max = 100
        current = 50

        binding.slider.addOnChangeListener { slider, value, fromUser ->
            val percent = value / (slider.valueTo - slider.valueFrom)
            _current = (min + percent * (max - min)).toInt()
            if (fromUser) {
                onValueChangedListener?.invoke(_current)
            }
        }


        binding.swAuto.setOnCheckedChangeListener { _, isChecked ->
            isAuto = isChecked
        }

        contentView = binding.root
    }
}