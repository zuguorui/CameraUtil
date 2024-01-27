package com.zu.camerautil.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView

abstract class AbsCameraParamView : LinearLayout {

    private val scrollView: ViewGroup

    private val itemContainer: LinearLayout

    private val items = ArrayList<View>()
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {

        itemContainer = LinearLayout(context).apply {
            this.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            this.orientation = this@AbsCameraParamView.orientation
            this.gravity = when (this@AbsCameraParamView.orientation) {
                HORIZONTAL -> Gravity.CENTER_VERTICAL
                else -> Gravity.CENTER_HORIZONTAL
            }
        }

        when (orientation) {
            HORIZONTAL -> {
                scrollView = HorizontalScrollView(context).apply {
                    this.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
            else -> {
                scrollView = ScrollView(context).apply {
                    this.layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            }
        }

        scrollView.addView(itemContainer)

        addView(scrollView)
    }

    fun setItems(items: List<View>) {
        this.items.clear()
        itemContainer.removeAllViews()
        this.items.addAll(items)
        for (item in items) {
            itemContainer.addView(item)
        }
        invalidate()
    }
}