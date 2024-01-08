package com.zu.camerautil.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Spinner

/**
 * @author zuguorui
 * @date 2024/1/8
 * @description
 */
class CameraSelectorView: FrameLayout {

    private lateinit var cameraSpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var sizeSpinner: Spinner

    private var cameraSpinnerRect = Rect()
    private var fpsSpinnerRect = Rect()
    private var sizeSpinnerRect = Rect()

    private val intervalV = 10
    private val intervalH = 10

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        cameraSpinner = Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(cameraSpinner)

        fpsSpinner = Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(fpsSpinner)

        sizeSpinner = Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(sizeSpinner)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var height = MeasureSpec.getSize(heightMeasureSpec)
        var heightMode = MeasureSpec.getMode(heightMeasureSpec)

        measureChildren(widthMeasureSpec, heightMeasureSpec)

        val views = arrayOf(cameraSpinner, fpsSpinner, sizeSpinner)
        val rectangles = arrayOf(cameraSpinnerRect, fpsSpinnerRect, sizeSpinnerRect)

        val availableLeft = paddingLeft
        val availableRight = width - paddingRight
        val availableTop = paddingTop
        var left = availableLeft
        var top = availableTop
        var lineBottom = top
        for (i in views.indices) {
            val view = views[i]
            val rect = rectangles[i]
            rect.set(0, 0, view.measuredWidth, view.measuredHeight)
            rect.offsetTo(left, top)
            if (rect.right > availableRight) {
                if (left != availableLeft) {
                    left = availableLeft
                    top = lineBottom + intervalV
                    rect.offsetTo(left, top)
                }
            }
            left = rect.right + intervalH
            lineBottom = Math.max(lineBottom, rect.bottom)
        }

        setMeasuredDimension(width, lineBottom + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val views = arrayOf(cameraSpinner, fpsSpinner, sizeSpinner)
        val rectangles = arrayOf(cameraSpinnerRect, fpsSpinnerRect, sizeSpinnerRect)

        for (i in views.indices) {
            val view = views[i]
            val rect = rectangles[i]
            view.layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }



}