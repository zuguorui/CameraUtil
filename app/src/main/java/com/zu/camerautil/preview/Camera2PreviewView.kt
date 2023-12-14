package com.zu.camerautil.preview

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2023/11/2
 * @description
 */
class Camera2PreviewView: FrameLayout {

    var surfaceView: SurfaceView
        private set

    val holder: SurfaceHolder
        get() = surfaceView.holder

    val surface: Surface
        get() = holder.surface

    private var surfaceRect = Rect()

    var scaleType = ScaleType.FILL_CENTER
        set(value) {
            field = value
            postInvalidate()
        }


    private var sourceResolution: Size? = null

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int): this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyle: Int): super(context, attributeSet, defStyleAttr, defStyle) {
        surfaceView = SurfaceView(context)
        surfaceView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(surfaceView)
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthSpec = MeasureSpec.getMode(widthMeasureSpec)
        var measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        var heightSpec = MeasureSpec.getMode(heightMeasureSpec)
        var measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        var resolution = sourceResolution ?: kotlin.run {
            surfaceRect = Rect(0, 0, measuredWidth, measuredHeight)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var rotate = display.rotation

        // Surface坐标系固定为自然方向，这里根据手机旋转来将其转换为view坐标方向
        var sourceRatio = when (rotate) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> resolution.run { width.toFloat() / height }
            else -> resolution.run { height.toFloat() / width }
        }

        var viewRatio = measuredWidth.toFloat() / measuredHeight

        var centerX = measuredWidth / 2
        var centerY = measuredHeight / 2

        var surfaceWidth: Int
        var surfaceHeight: Int

        if (viewRatio >= sourceRatio) {
            // 图片比view窄
            when (scaleType) {
                ScaleType.FIT_CENTER -> {
                    surfaceHeight = measuredHeight
                    surfaceWidth = (surfaceHeight * sourceRatio).toInt()
                }
                else -> {
                    surfaceWidth = measuredWidth
                    surfaceHeight = (surfaceWidth / sourceRatio).toInt()
                }
            }
        } else {
            // 图片比view宽
            when (scaleType) {
                ScaleType.FIT_CENTER -> {
                    surfaceWidth = measuredWidth
                    surfaceHeight = (surfaceWidth / sourceRatio).toInt()
                }
                else -> {
                    surfaceHeight = measuredHeight
                    surfaceWidth = (surfaceHeight * sourceRatio).toInt()
                }
            }
        }

        surfaceRect.apply {
            left = centerX - surfaceWidth / 2
            right = left + surfaceWidth
            top = centerY - surfaceHeight / 2
            bottom = top + surfaceHeight
        }
        var surfaceWidthSpec = MeasureSpec.makeMeasureSpec(surfaceWidth, MeasureSpec.EXACTLY)
        var surfaceHeightSpec = MeasureSpec.makeMeasureSpec(surfaceHeight, MeasureSpec.EXACTLY)
        surfaceView.measure(surfaceWidthSpec, surfaceHeightSpec)

        setMeasuredDimension(measuredWidth, measuredHeight)
        Timber.d("onMeasure: surfaceRect = $surfaceRect, sourceResolution = $sourceResolution, viewSize = ${Size(width, height)}")
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        surfaceView.layout(surfaceRect.left, surfaceRect.top, surfaceRect.right, surfaceRect.bottom)
    }

    fun setSourceResolution(width: Int, height: Int) {
        sourceResolution = Size(width, height)
        surfaceView.holder.setFixedSize(width, height)
        requestLayout()
    }

    fun getSourceResolution(): Size? {
        return sourceResolution?.let {
            Size(it.width, it.height)
        }
    }





    enum class ScaleType {
        FILL_CENTER,
        FIT_CENTER
    }

    companion object {
        private const val TAG = "Camera2PreviewView"
    }


}