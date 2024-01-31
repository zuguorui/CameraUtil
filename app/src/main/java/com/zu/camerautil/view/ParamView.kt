package com.zu.camerautil.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.zu.camerautil.databinding.ItemCameraParamBinding

class ParamView: FrameLayout {

    private val layoutInflater: LayoutInflater
    private val binding: ItemCameraParamBinding

    var
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        layoutInflater = LayoutInflater.from(context)
        binding = ItemCameraParamBinding.inflate(layoutInflater, this, false)
        addView(binding.root)
    }


}