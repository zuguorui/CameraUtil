package com.zu.camerautil.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import timber.log.Timber
import java.nio.FloatBuffer
import java.nio.IntBuffer

typealias GLES = GLES30
typealias GLESExt = GLES11Ext

class GLRender {

    val context: Context

    private val glThread = HandlerThread("gl-thread").apply {
        start()
    }
    private val glHandler = Handler(glThread.looper)

    var inputSurface: InputSurface? = null
        private set

    private var outputSurfaceList = ArrayList<OutputSurface>()

    private var eglCore: EGLCore? = null
    private var oesShader: Shader = Shader()

    var scaleType = ScaleType.INSIDE
        private set

    private var VAO: Int = 0
    private var VBO: Int = 0
    private var EBO: Int = 0
    // 坐标坐标。屏幕屏幕中间为原点，纹理坐标左下角为原点，向右向上为正。注意纹理与屏幕上下是相反的
    private var vertices = floatArrayOf(
        -1f, -1f, 0f, 0f, 1f, // 左下
         1f, -1f, 0f, 1f, 1f, // 右下
         1f,  1f, 0f, 1f, 0f, // 右上
        -1f,  1f, 0f, 0f, 0f  // 左上
    )

    constructor(context: Context) {
        this.context = context
        glHandler.post {
            initInner()
        }
    }

    private fun initInner() {
        Timber.d("initInner")
        eglCore = EGLCore()
        val eglCore = eglCore!!
        if (!eglCore.isReady) {
            Timber.e("eglCore is not ready")
            return
        }

        if (!oesShader.compile(vertShaderCode, oesFragShaderCode)) {
            Timber.e("compile oes shader failed")
            return
        }

        inputSurface = createInputSurface()
        initVertex()
    }

    private fun initVertex() {
        val vao = IntArray(1)
        val vbo = IntArray(1)
        val ebo = IntArray(1)
        GLES.glGenVertexArrays(1, vao, 0)
        GLES.glGenBuffers(1, vbo, 0)
        GLES.glGenBuffers(1, ebo, 0)

        VAO = vao[0]
        VBO = vbo[0]
        EBO = ebo[0]

        GLES.glBindVertexArray(VAO)
        GLES.glBindBuffer(GLES.GL_ARRAY_BUFFER, VBO)
        val vboBuffer = FloatBuffer.allocate(vertices.size).put(vertices)
        GLES.glBufferData(GLES.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, vboBuffer, GLES.GL_STATIC_DRAW)
        GLES.glVertexAttribPointer(0, 3, GLES.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
        GLES.glEnableVertexAttribArray(0)
        GLES.glVertexAttribPointer(1, 2, GLES.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES)
        GLES.glEnableVertexAttribArray(1)

        val eboBuffer = IntBuffer.allocate(VERTEX_INDICES.size).put(VERTEX_INDICES)
        GLES.glBindBuffer(GLES.GL_ELEMENT_ARRAY_BUFFER, EBO)
        GLES.glBufferData(GLES.GL_ELEMENT_ARRAY_BUFFER, VERTEX_INDICES.size * Int.SIZE_BYTES, eboBuffer, GLES.GL_STATIC_DRAW)

        GLES.glBindVertexArray(0)
    }

    private fun updateVertex(texWidth: Int, texHeight: Int, screenWidth: Int, screenHeight: Int, scaleType: ScaleType): Boolean {
        if (texWidth <= 0 || texHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            Timber.e("size must be >= 0")
            return false
        }
        // 初始为整个纹理
        var texLeft = 0f
        var texTop = 1f
        var texRight = 1f
        var texBottom = 0f

        // 初始为整个屏幕
        var screenLeft = -1f
        var screenTop = 1f
        var screenRight = 1f
        var screenBottom = -1f

        val texW2H = texWidth.toFloat() / texHeight
        val screenW2H = screenWidth.toFloat() / screenHeight

        when (scaleType) {
            ScaleType.SCALE_FULL -> {
                // 什么也不做，用初始值即可
            }
            ScaleType.FULL -> {
                // 全屏显示，保持宽高比
                if (texW2H >= screenW2H) {
                    // 纹理比屏幕宽，则保持纹理高度，裁剪纹理宽度。纹理左右有隐藏
                    val scaledTexWidth = texHeight * screenW2H
                    val s = scaledTexWidth / texWidth
                    texLeft = (1 - s) / 2
                    texRight = texLeft + s
                } else {
                    // 纹理比屏幕高，则保持纹理宽度，裁剪纹理高度。纹理上下有隐藏
                    val scaledTexHeight = texWidth / screenW2H
                    val s = scaledTexHeight / texHeight
                    texBottom = (1 - s) / 2
                    texTop = texBottom + s
                }
            }
            ScaleType.INSIDE -> {
                // 纹理完全显示，保持宽高比
                if (texW2H >= screenW2H) {
                    // 纹理比屏幕宽，保持屏幕宽度，裁剪屏幕高度。屏幕上下有黑边
                    val scaledScreenHeight = screenWidth / texW2H
                    val s = scaledScreenHeight / screenHeight
                    screenTop = s / 2
                    screenBottom = -screenTop
                } else {
                    // 纹理比屏幕高，保持屏幕高度，裁剪屏幕宽度，屏幕左右有黑边
                    val scaledScreenWidth = screenHeight * texW2H
                    val s = scaledScreenWidth / screenWidth
                    screenLeft = -s / 2
                    screenRight = -screenLeft
                }
            }
        }

        vertices = floatArrayOf(
            screenLeft, screenBottom, 0f, texLeft, texTop,
            screenRight, screenBottom, 0f, texRight, texTop,
            screenRight, screenTop, 0f, texRight, texBottom,
            screenLeft, screenTop, 0f, texLeft, texBottom
        )
        return true
    }

    private fun addOutputSurfaceInner(surfaceObj: Any, width: Int, height: Int) {
        val eglCore = eglCore ?: return
        val outputSurface = OutputSurface(eglCore, surfaceObj, width, height)
        if (!outputSurface.isReady) {
            Timber.e("addSurfaceInner failed")
            return
        }
        outputSurfaceList.add(outputSurface)
    }

    private fun removeOutputSurfaceInner(surfaceObj: Any) {
        outputSurfaceList.removeIf {
            it.surface == surfaceObj
        }
    }

    private fun changeOutputSizeInner(surfaceObj: Any, width: Int, height: Int) {
        val target = outputSurfaceList.find {
            it.surface == surfaceObj
        }
        target?.setSize(width, height)
    }

    private fun changeInputSizeInner(width: Int, height: Int) {
        inputSurface?.setSize(width, height)
    }

    private fun createInputSurface(): InputSurface {
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
        var inputSurface = InputSurface(tex[0])
        inputSurface.surfaceTexture.setOnFrameAvailableListener(
            OnFrameAvailableListener {
                draw()
            },
            glHandler
        )
        return inputSurface
    }

    private fun draw() {
        val eglCore = eglCore ?: return
        val inputSurface = inputSurface ?: return
        if (!eglCore.isReady) {
            return
        }
        if (!oesShader.isReady) {
            return
        }

        inputSurface.surfaceTexture.updateTexImage()

        oesShader.use()
        GLES.glActiveTexture(GLES.GL_TEXTURE0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, inputSurface.textureId)
        oesShader.setInt("tex", 0)

        GLES.glBindVertexArray(VAO)
        for (outputSurface in outputSurfaceList) {
            eglCore.makeCurrent(outputSurface)
            if (!updateVertex(inputSurface.width, inputSurface.height, outputSurface.width, outputSurface.height, scaleType)) {
                continue
            }
            GLES.glBindBuffer(GLES.GL_ARRAY_BUFFER, VBO)
            val vboBuffer = FloatBuffer.allocate(vertices.size).put(vertices)
            GLES.glBufferData(GLES.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, vboBuffer, GLES.GL_STATIC_DRAW)

            GLES.glClearColor(0f, 0f, 0f, 0f)
            GLES.glClear(GLES.GL_COLOR_BUFFER_BIT)
            GLES.glDrawElements(GLES.GL_TRIANGLES, 6, GLES.GL_INT, 0)
            eglCore.swapBuffers(outputSurface)
        }
        GLES.glBindVertexArray(0)
        oesShader.endUse()
    }

    fun addOutputSurface(surfaceObj: Any, width: Int, height: Int) {
        if (surfaceObj !is Surface && surfaceObj !is SurfaceTexture) {
            return
        }
        glHandler.post {
            addOutputSurfaceInner(surfaceObj, width, height)
        }
    }

    fun removeOutputSurface(surfaceObj: Any) {
        if (surfaceObj !is Surface && surfaceObj !is SurfaceTexture) {
            return
        }
        glHandler.post {
            removeOutputSurfaceInner(surfaceObj)
        }
    }

    fun changeOutputSize(surfaceObj: Any, width: Int, height: Int) {
        if (surfaceObj !is Surface && surfaceObj !is SurfaceTexture) {
            return
        }
        glHandler.post {
            changeOutputSizeInner(surfaceObj, width, height)
        }
    }

    fun changeInputSize(width: Int, height: Int) {
        glHandler.post {
            changeInputSizeInner(width, height)
        }
    }


    companion object {
        private val VERTEX_INDICES = intArrayOf(
            0, 3, 2,
            2, 1, 0
        )
    }

    enum class ScaleType {
        // 纹理完整在屏幕内显示，保持宽高比。可能会在屏幕内留黑边
        INSIDE,
        // 纹理占满整个屏幕，保持宽高比。可能纹理的一部分会超出屏幕范围
        FULL,
        // 纹理占满整个屏幕，并且缩放成与屏幕一样的宽高比。可能会导致纹理变形
        SCALE_FULL;
    }
}