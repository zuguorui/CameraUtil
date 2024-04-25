package com.zu.camerautil.gl

import android.opengl.EGLSurface
import timber.log.Timber

class OutputSurface {
    val eglCore: EGLCore
    val surface: Any

    var eglSurface: EGLSurface = EGL.EGL_NO_SURFACE
        private set

    constructor(eglCore: EGLCore, surface: Any) {
        this.eglCore = eglCore
        this.surface = surface
        val attrArray = IntArray(1)
        if (!EGL.eglGetConfigAttrib(eglCore.eglDisplay, eglCore.eglConfig, EGL.EGL_NATIVE_VISUAL_ID, attrArray, 0)) {
            Timber.e("get config attrib failed, error = %x", EGL.eglGetError())
            return
        }
        eglSurface = EGL.eglCreateWindowSurface(eglCore.eglDisplay, eglCore.eglConfig!!, surface, attrArray, 0)
        if (eglSurface == EGL.EGL_NO_SURFACE) {
            Timber.e("create windowSurface failed, error = %x", EGL.eglGetError())
            return
        }
    }

    fun swapBuffers() {
        if (!eglCore.isReady) {
            return
        }
        if (!EGL.eglSwapBuffers(eglCore.eglDisplay, eglSurface)) {
            Timber.e("swapBuffers failed, error = %x", EGL.eglGetError())
        }
    }
}