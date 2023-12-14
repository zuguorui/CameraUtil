package com.zu.camerautil.preview

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.widget.FrameLayout

/**
 * @author zuguorui
 * @date 2023/11/2
 * @description
 */
class Camera2PreviewView: FrameLayout {

    private lateinit var implementation: PreviewViewImplementation

    val implementationType: ImplementationType
        get() = when (implementation) {
            is SurfaceViewImplementation -> ImplementationType.SURFACE_VIEW
            is TextureViewImplementation -> ImplementationType.TEXTURE_VIEW
            else -> throw RuntimeException("unknown implementation type")
        }

    val surface: Surface
        get() = implementation.surface

    val surfaceSize: Size
        get() = implementation.surfaceSize

    var previewSize: Size
        get() = implementation.previewSize
        set(value) {
            implementation.previewSize = value
        }

    var scaleType: ScaleType
        set(value) {
            implementation.scaleType = value
        }
        get() = implementation.scaleType


    var surfaceStateListener: PreviewViewImplementation.SurfaceStateListener? = null
        set(value) {
            field = value
            implementation.surfaceStateListener = value
        }

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int): this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyle: Int): super(context, attributeSet, defStyleAttr, defStyle) {
        implementation = createImplementation(ImplementationType.SURFACE_VIEW)
    }

    private fun createImplementation(type: ImplementationType): PreviewViewImplementation {
        val impl = if (type == ImplementationType.SURFACE_VIEW) {
            SurfaceViewImplementation(context)
        } else {
            TextureViewImplementation(context)
        }
        impl.attachToParent(this)
        impl.surfaceStateListener = surfaceStateListener
        return impl
    }

    fun setImplementationType(type: ImplementationType) {
        implementation.detachFromParent()
        implementation = createImplementation(type)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
        implementation.measure(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        implementation.layout()
    }


    enum class ScaleType {
        FILL_CENTER,
        FIT_CENTER
    }

    enum class ImplementationType {
        SURFACE_VIEW,
        TEXTURE_VIEW
    }

    companion object {
        private const val TAG = "Camera2PreviewView"
    }


}