package com.zu.camerautil.gl

import android.content.Context
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import timber.log.Timber

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

    var inputSurfaceListener: InputSurfaceListener? = null

    private var VAO: Int = 0
    private var VBO: Int = 0
    private var EBO: Int = 0

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
        inputSurfaceListener?.onSurfaceAvailable(inputSurface!!.surface, inputSurface!!.width, inputSurface!!.height)
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

    private fun changeOutputSurfaceSizeInner(surfaceObj: Any, width: Int, height: Int) {
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
        if (!oesShader.isReady) {
            return
        }
        oesShader.use()
        for (outputSurface in outputSurfaceList) {
            eglCore!!.makeCurrent(outputSurface)
        }
    }


    companion object {
        private val VERTEX_INDICES = intArrayOf(
            0, 3, 2,
            2, 1, 0
        )
    }

    interface InputSurfaceListener {
        fun onSurfaceAvailable(surface: Surface, width: Int, height: Int)
        fun onSurfaceSizeChanged(surface: Surface, width: Int, height: Int)
    }

}