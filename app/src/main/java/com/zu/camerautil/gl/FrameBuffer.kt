package com.zu.camerautil.gl

/**
 * 帧缓冲，只有颜色附件
 * */
class FrameBuffer {
    var FBO: Int
    var colorTex: Int
        private set

    var width = 1920
        private set
    var height = 1080
        private set

    val isReady: Boolean
        get() = FBO > 0 && colorTex > 0
    var isReleased = false
        private set


    constructor(width: Int, height: Int) {
        this.width = width
        this.height = height

        val fbo = IntArray(1)
        GLES.glGenBuffers(1, fbo, 0)
        FBO = fbo[0]
        GLES.glBindFramebuffer(GLES.GL_FRAMEBUFFER, FBO)
        colorTex = createTexture(width, height)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, colorTex)
        GLES.glFramebufferTexture2D(GLES.GL_FRAMEBUFFER, GLES.GL_COLOR_ATTACHMENT0, GLES.GL_TEXTURE_2D, colorTex, 0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, 0)
        GLES.glBindFramebuffer(GLES.GL_FRAMEBUFFER, 0)
    }

    private fun createTexture(width: Int, height: Int): Int {
        val tex = IntArray(1)
        GLES.glGenTextures(1, tex, 0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, tex[0])

        GLES.glTexParameteri(
            GLES.GL_TEXTURE_2D,
            GLES.GL_TEXTURE_MIN_FILTER,
            GLES.GL_LINEAR
        )
        GLES.glTexParameteri(
            GLES.GL_TEXTURE_2D,
            GLES.GL_TEXTURE_MAG_FILTER,
            GLES.GL_LINEAR
        )
        GLES.glTexParameteri(
            GLES.GL_TEXTURE_2D,
            GLES.GL_TEXTURE_WRAP_S,
            GLES.GL_CLAMP_TO_EDGE
        )
        GLES.glTexParameteri(
            GLES.GL_TEXTURE_2D,
            GLES.GL_TEXTURE_WRAP_T,
            GLES.GL_CLAMP_TO_EDGE
        )

        GLES.glTexImage2D(GLES.GL_TEXTURE_2D, 0, GLES.GL_RGB, width, height, 0, GLES.GL_RGB, GLES.GL_UNSIGNED_BYTE, null)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, 0)

        return tex[0]
    }

    fun setSize(width: Int, height: Int) {
        if (this.width == width && height == this.height) {
            return
        }
        this.width = width
        this.height = height

        if (FBO <= 0 || colorTex <= 0) {
            return
        }
        GLES.glBindFramebuffer(GLES.GL_FRAMEBUFFER, FBO)
        GLES.glDeleteTextures(1, intArrayOf(colorTex), 0)
        colorTex = createTexture(width, height)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, colorTex)
        GLES.glFramebufferTexture2D(GLES.GL_FRAMEBUFFER, GLES.GL_COLOR_ATTACHMENT0, GLES.GL_TEXTURE_2D, colorTex, 0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, 0)
        GLES.glBindFramebuffer(GLES.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        isReleased = true

        if (colorTex > 0) {
            GLES.glDeleteTextures(1, intArrayOf(colorTex), 0)
            colorTex = 0
        }

        if (FBO > 0) {
            GLES.glDeleteBuffers(1, intArrayOf(FBO), 0)
            FBO = 0
        }
    }

}