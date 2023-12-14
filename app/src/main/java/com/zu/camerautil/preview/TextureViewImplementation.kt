package com.zu.camerautil.preview

import android.content.Context
import android.graphics.Rect
import android.util.Size
import android.view.Surface
import android.view.ViewGroup

class TextureViewImplementation: PreviewViewImplementation {

    override var previewSize: Size
        get() = TODO("Not yet implemented")
        set(value) {}
    override val surfaceSize: Size
        get() = TODO("Not yet implemented")

    constructor(context: Context): super(context) {

    }
    override val surface: Surface
        get() = TODO("Not yet implemented")

    override fun onMeasure(bound: Rect) {
        TODO("Not yet implemented")
    }

    override fun onLayout(bound: Rect) {
        TODO("Not yet implemented")
    }

    override fun requestAttachToParent(viewGroup: ViewGroup) {
        TODO("Not yet implemented")
    }

    override fun requestDetachFromParent(viewGroup: ViewGroup) {
        TODO("Not yet implemented")
    }


}