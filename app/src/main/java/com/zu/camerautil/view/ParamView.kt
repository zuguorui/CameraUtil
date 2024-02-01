package com.zu.camerautil.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.zu.camerautil.bean.IDisplayParam
import com.zu.camerautil.bean.OnValueChangedListener
import com.zu.camerautil.databinding.ItemCameraParamBinding

class ParamView: FrameLayout {

    private val layoutInflater: LayoutInflater
    private val binding: ItemCameraParamBinding

    private val valueListener: OnValueChangedListener<*> = {
        notifyDataChanged()
    }

    var param: IDisplayParam? = null
        set(value) {
            field = value
            notifyDataChanged()
        }

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        layoutInflater = LayoutInflater.from(context)
        binding = ItemCameraParamBinding.inflate(layoutInflater, this, false)
        addView(binding.root)
        isClickable = true
        notifyDataChanged()
    }

    fun notifyDataChanged() {
        param?.let {
            binding.tvName.text = it.name
            binding.tvValue.text = it.currentValue
            if (it.isModal) {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = it.currentMode
            } else {
                binding.tvMode.visibility = View.GONE
            }
        }
    }


}