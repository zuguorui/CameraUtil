package com.zu.camerautil.gl

import android.graphics.SurfaceTexture
import android.view.Surface

/**
 * @author zuguorui
 * @date 2024/4/26
 * @description
 */
class InputSurface {
    val surface: Surface
    val surfaceTexture: SurfaceTexture
    val textureId: Int

    var width = 1920
        private set

    var height = 1080
        private set

    var sizeModified = false
    constructor(textureId: Int) {
        this.textureId = textureId
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(width, height)
        surface = Surface(surfaceTexture)
    }

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
        surfaceTexture.setDefaultBufferSize(width, height)
    }
}