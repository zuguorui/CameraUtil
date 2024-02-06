package com.zu.camerautil.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.PopupWindow
import timber.log.Timber

open class EasyLayoutPopupWindow: PopupWindow {

    val context: Context

    constructor(context: Context): super(context) {
        this.context = context
    }

    open fun show(anchorView: View, gravity: Int = Gravity.NO_GRAVITY) {
        if (contentView == null) {
            return
        }
        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)

        val rootView = anchorView.rootView
        val rootLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)

        val (widthSpec, heightSpec) = getContentMeasureSpec(rootView.width, rootView.height)

        contentView.measure(widthSpec, heightSpec)

        Timber.d("show, root.width = ${rootView.width}, root.height = ${rootView.height}")
        Timber.d("show, measuredWidth = ${contentView.measuredWidth}, measuredHeight = ${contentView.measuredHeight}")

        val anchorLeft = anchorLocation[0]
        val anchorTop = anchorLocation[1]
        val anchorRight = anchorLeft + anchorView.width
        val anchorBottom = anchorTop + anchorView.height

        val rootLeft = rootLocation[0]
        val rootTop = rootLocation[1]
        val rootRight = rootLeft + rootView.width
        val rootBottom = rootTop + rootView.height

        val topSpace = anchorTop - rootTop
        val leftSpace = anchorLeft - rootLeft
        val bottomSpace = rootBottom - anchorBottom
        val rightSpace = rootRight - anchorRight

        var left: Int = 0
        var top: Int = 0
        var right: Int = 0
        var bottom: Int = 0

        val placeTop = gravity and Gravity.TOP == Gravity.TOP
        val placeBottom = gravity and Gravity.BOTTOM == Gravity.BOTTOM
        val placeLeft = gravity and Gravity.LEFT == Gravity.LEFT
        val placeRight = gravity and Gravity.RIGHT == Gravity.RIGHT

        if (placeTop || placeBottom) {
            if (placeTop) {
                bottom = anchorTop
                top = Math.max(rootTop, bottom - contentView.measuredHeight)
            } else {
                top = anchorBottom
                bottom = Math.min(rootBottom, top + contentView.measuredHeight)
            }

            if (!(placeLeft || placeRight)) {
                left = Math.max(rootLeft, Math.min(anchorLeft, rootRight - contentView.measuredWidth))
                right = Math.min(rootRight, left + contentView.measuredWidth)
            }
        }

        if (placeLeft || placeRight) {
            if (placeLeft) {
                right = anchorLeft
                left = Math.max(rootLeft, right - contentView.measuredWidth)
            } else {
                left = anchorRight
                right = Math.min(rootRight, left + contentView.measuredWidth)
            }
            if (!(placeTop || placeBottom)) {
                top = Math.max(rootTop, Math.min(anchorTop, rootBottom - contentView.measuredHeight))
                bottom = Math.min(rootBottom, top + contentView.measuredHeight)
            }
        }

        if (!(placeLeft || placeRight || placeTop || placeBottom)) {
            if (topSpace >= contentView.measuredHeight || bottomSpace >= contentView.measuredHeight) {
                // 先看上下是否可以布局
                if (bottomSpace >= contentView.measuredHeight) {
                    top = anchorBottom
                    bottom = Math.min(rootBottom, top + contentView.measuredHeight)
                } else {
                    bottom = anchorTop
                    top = Math.max(rootTop, bottom - contentView.measuredHeight)
                }

                left = Math.max(rootLeft, Math.min(anchorLeft, rootRight - contentView.measuredWidth))
                right = Math.min(rootRight, left + contentView.measuredWidth)
            } else if (leftSpace >= contentView.measuredWidth || rightSpace >= contentView.measuredWidth) {
                // 再看左右是否足够布局
                if (leftSpace >= contentView.measuredWidth) {
                    right = anchorLeft
                    left = Math.max(rootLeft, right - contentView.measuredWidth)
                } else {
                    left = anchorRight
                    right = Math.min(rootRight, left + contentView.measuredWidth)
                }

                top = Math.max(rootTop, Math.min(anchorTop, rootBottom - contentView.measuredHeight))
                bottom = Math.min(rootBottom, top + contentView.measuredHeight)
            } else {
                // 上下左右都不够，那就从上面或者下面找个位置相对大的
                if (bottomSpace >= topSpace) {
                    top = anchorBottom
                    bottom = Math.min(rootBottom, top + contentView.measuredHeight)
                } else {
                    bottom = anchorTop
                    top = Math.max(rootTop, bottom - contentView.measuredHeight)
                }
                left = Math.max(rootLeft, Math.min(anchorLeft, rootRight - contentView.measuredWidth))
                right = Math.min(rootRight, left + contentView.measuredWidth)
            }
        }


        Timber.d("show, left = $left, right = $right, top = $top, bottom = $bottom")
        this@EasyLayoutPopupWindow.width = right - left
        this@EasyLayoutPopupWindow.height = bottom - top

        showAtLocation(anchorView, Gravity.LEFT or Gravity.TOP, left, top)
    }

    private fun getContentMeasureSpec(parentWidth: Int, parentHeight: Int): Pair<Int, Int> {
        if (contentView == null) {
            throw IllegalStateException("content view is null")
        }
        var width = contentView.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
        var height = contentView.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT


        val widthSpec = when (width) {
            ViewGroup.LayoutParams.WRAP_CONTENT -> {
                MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.AT_MOST)
            }
            ViewGroup.LayoutParams.MATCH_PARENT -> {
                MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.EXACTLY)
            }
            else -> {
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            }
        }

        val heightSpec = when (height) {
            ViewGroup.LayoutParams.WRAP_CONTENT -> {
                MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.AT_MOST)
            }
            ViewGroup.LayoutParams.MATCH_PARENT -> {
                MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.EXACTLY)
            }
            else -> {
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            }
        }

        return Pair(widthSpec, heightSpec)
    }


}