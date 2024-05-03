package com.zu.camerautil.gl

import android.graphics.SurfaceTexture
import android.view.Surface
import java.nio.FloatBuffer
import java.nio.IntBuffer

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


    constructor() {
        val tex = IntArray(1)
        GLES.glGenTextures(1, tex, 0)
        GLES.glBindTexture(GLESExt.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_MIN_FILTER,
            GLES.GL_LINEAR
        )
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_MAG_FILTER,
            GLES.GL_LINEAR
        )
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_WRAP_S,
            GLES.GL_CLAMP_TO_EDGE
        )
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_WRAP_T,
            GLES.GL_CLAMP_TO_EDGE
        )
        GLES.glBindTexture(GLESExt.GL_TEXTURE_EXTERNAL_OES, 0)

        textureId = tex[0]
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


    fun release() {
        if (isReleased) {
            return
        }
        surfaceTexture.release()
        surface.release()
    }

}