package com.zu.camerautil.view.preview

import android.content.Context
import android.util.Size
import android.view.Surface

/**
 * @author zuguorui
 * @date 2023/12/12
 * @description
 */
abstract class PreviewViewImplementation {

    var surfaceListener: SurfaceListener? = null

    var scaleType: Camera2PreviewView.ScaleType = Camera2PreviewView.ScaleType.FIT_CENTER

    constructor(context: Context)

    abstract val surface: Surface

    abstract fun setSourceResolution(width: Int, height: Int)

    abstract fun getSourceResolution(): Size
}