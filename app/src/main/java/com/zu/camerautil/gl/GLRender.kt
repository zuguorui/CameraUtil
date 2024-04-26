package com.zu.camerautil.gl

import android.content.Context
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.view.Surface
import timber.log.Timber

typealias GLES = GLES30
typealias GLESExt = GLES11Ext

class GLRender {

    val context: Context

    private val glCallback = Handler.Callback {
        when (it.what) {
            GL_MSG_INIT -> {
                initInner()
            }
            GL_MSG_ADD_SURFACE -> {
                val surface = it.obj
                addSurfaceInner(surface)
            }
            GL_MSG_REMOVE_SURFACE -> {
                val surface = it.obj
                removeSurfaceInner(surface)
            }
            GL_MSG_CHANGE_SIZE -> {
                changeSizeInner()
            }
        }
        true
    }

    private val glThread = HandlerThread("gl-thread").apply {
        start()
    }
    private val glHandler = Handler(glThread.looper, glCallback)

    private var width: Int = 1920
    private var height: Int = 1080

    private var inputSurface: InputSurface? = null
    private var outputSurfaceList = ArrayList<OutputSurface>()

    private var eglCore: EGLCore? = null

    var inputSurfaceListener: InputSurfaceListener? = null

    constructor(context: Context) {
        this.context = context
        glHandler.sendMessage(
            Message.obtain().apply {
                what = GL_MSG_INIT
            }
        )
    }

    private fun initInner() {
        eglCore = EGLCore()
        val eglCore = eglCore!!
        if (!eglCore.isReady) {
            Timber.e("eglCore is not ready")
            return
        }

        inputSurface = createInputSurface()
        inputSurfaceListener?.onSurfaceAvailable(inputSurface!!.surface, width, height)
    }

    private fun addSurfaceInner(surfaceObj: Any) {
        val eglCore = eglCore ?: return
        val outputSurface = OutputSurface(eglCore, surfaceObj)
        if (!outputSurface.isReady) {
            Timber.e("addSurfaceInner failed")
            return
        }
        outputSurfaceList.add(outputSurface)
    }

    private fun removeSurfaceInner(surfaceObj: Any) {
        outputSurfaceList.removeIf {
            it.surface == surfaceObj
        }
    }

    private fun changeSizeInner() {

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

    }


    companion object {
        private const val GL_MSG_INIT = 0
        private const val GL_MSG_ADD_SURFACE = 1
        private const val GL_MSG_REMOVE_SURFACE = 2
        private const val GL_MSG_CHANGE_SIZE = 3
    }

    interface InputSurfaceListener {
        fun onSurfaceAvailable(surface: Surface, width: Int, height: Int)
        fun onSurfaceSizeChanged(surface: Surface, width: Int, height: Int)
    }

}