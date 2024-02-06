package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.zu.camerautil.R

abstract class AbsCameraParamView : LinearLayout {

    private val scrollView: ViewGroup

    private val itemContainer: LinearLayout

    private val items = ArrayList<View>()

    var paramPanelPopupGravity: Int = Gravity.NO_GRAVITY
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {

        val typeArray = context.obtainStyledAttributes(attributeSet, R.styleable.AbsCameraParamView)

        val gravityInt = typeArray.getInt(R.styleable.AbsCameraParamView_paramPanelPopupGravity, 0)

        if (gravityInt and ATTR_PANEL_GRAVITY_LEFT == ATTR_PANEL_GRAVITY_LEFT) {
            paramPanelPopupGravity = paramPanelPopupGravity.or(Gravity.LEFT)
        } else if (gravityInt and ATTR_PANEL_GRAVITY_RIGHT == ATTR_PANEL_GRAVITY_RIGHT) {
            paramPanelPopupGravity = paramPanelPopupGravity.or(Gravity.RIGHT)
        }
        if (gravityInt and ATTR_PANEL_GRAVITY_BOTTOM == ATTR_PANEL_GRAVITY_BOTTOM) {
            paramPanelPopupGravity = paramPanelPopupGravity.or(Gravity.BOTTOM)
        } else if (gravityInt and ATTR_PANEL_GRAVITY_TOP == ATTR_PANEL_GRAVITY_TOP) {
            paramPanelPopupGravity = paramPanelPopupGravity.or(Gravity.TOP)
        }

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
        isFocusable = true
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

    companion object {
        private const val ATTR_PANEL_GRAVITY_LEFT = 1
        private const val ATTR_PANEL_GRAVITY_RIGHT = 2
        private const val ATTR_PANEL_GRAVITY_TOP = 4
        private const val ATTR_PANEL_GRAVITY_BOTTOM = 8
    }
}