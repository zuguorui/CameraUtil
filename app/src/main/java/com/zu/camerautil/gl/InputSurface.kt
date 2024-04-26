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

    constructor(textureId: Int) {
        this.textureId = textureId
        surfaceTexture = SurfaceTexture(textureId)
        surface = Surface(surfaceTexture)
    }
}