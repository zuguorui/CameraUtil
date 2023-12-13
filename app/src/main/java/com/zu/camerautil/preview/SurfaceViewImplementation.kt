package com.zu.camerautil.preview

import android.content.Context
import android.util.Size
import android.view.Surface

/**
 * @author zuguorui
 * @date 2023/12/13
 * @description
 */
class SurfaceViewImplementation: PreviewViewImplementation {

    constructor(context: Context): super(context) {

    }

    override val surface: Surface
        get() = TODO("Not yet implemented")

    override fun setSourceResolution(width: Int, height: Int) {
        TODO("Not yet implemented")
    }

    override fun getSourceResolution(): Size {
        TODO("Not yet implemented")
    }
}