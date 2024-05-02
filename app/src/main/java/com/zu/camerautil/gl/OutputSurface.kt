package com.zu.camerautil.gl

import android.opengl.EGLSurface
import timber.log.Timber

class OutputSurface {
    val eglCore: EGLCore
    val surface: Any

    var width: Int = 1920
        private set

    var height: Int = 1080
        private set

    var degree: Int = 0
        private set

    var eglSurface: EGLSurface = EGL.EGL_NO_SURFACE
        private set

    val isReady: Boolean
        get() = eglSurface != EGL.EGL_NO_SURFACE



    constructor(eglCore: EGLCore, surface: Any, width: Int, height: Int) {
        this.eglCore = eglCore
        this.surface = surface
        this.width = width
        this.height = height
//        val attrArray = IntArray(1)
//        if (!EGL.eglGetConfigAttrib(eglCore.eglDisplay, eglCore.eglConfig, EGL.EGL_NATIVE_VISUAL_ID, attrArray, 0)) {
//            Timber.e("get config attrib failed, error = %x", EGL.eglGetError())
//            return
//        }
        val attrArray = intArrayOf(
            EGL.EGL_NONE
        )
        eglSurface = EGL.eglCreateWindowSurface(eglCore.eglDisplay, eglCore.eglConfig!!, surface, attrArray, 0)
        if (eglSurface == EGL.EGL_NO_SURFACE) {
            Timber.e("create windowSurface failed, error = %x", EGL.eglGetError())
            return
        }
    }

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun setRotate(degree: Int) {
        this.degree = degree / 90 * 90
    }

    fun release() {
        if (eglSurface != EGL.EGL_NO_SURFACE) {
            EGL.eglDestroySurface(eglCore.eglDisplay, eglSurface)
            eglSurface = EGL.EGL_NO_SURFACE
        }
    }
}