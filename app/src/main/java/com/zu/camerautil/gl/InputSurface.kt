package com.zu.camerautil.gl

import android.graphics.SurfaceTexture
import android.view.Surface

/**
 * @author zuguorui
 * @date 2024/4/26
 * @description
 */
class InputSurface {
    var isReleased = false
    val surface: Surface
    val surfaceTexture: SurfaceTexture
    val textureId: Int

    var width = 1920
        private set

    var height = 1080
        private set

    var degree = 0
        private set

    constructor(textureId: Int) {
        this.textureId = textureId
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(width, height)
        surface = Surface(surfaceTexture)
        isReleased = false
    }

    fun setSize(width: Int, height: Int) {
        if (isReleased) {
            return
        }
        this.width = width
        this.height = height
        surfaceTexture.setDefaultBufferSize(width, height)
    }

    fun setRotate(degree: Int) {
        this.degree = degree / 90 * 90
    }

    fun release() {
        if (isReleased) {
            return
        }
        surfaceTexture.release()
        surface.release()
    }
}