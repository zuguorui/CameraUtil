package com.zu.camerautil.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import timber.log.Timber
import java.lang.Exception

typealias EGL = EGL14
class EGLCore {
    var eglContext: EGLContext = EGL.EGL_NO_CONTEXT
        private set

    var eglDisplay: EGLDisplay = EGL.EGL_NO_DISPLAY
        private set

    var sharedEglContext: EGLContext? = null
        private set

    var eglConfig: EGLConfig? = null

    val isReady: Boolean
        get() = eglContext != EGL.EGL_NO_CONTEXT && eglDisplay != EGL.EGL_NO_DISPLAY && eglConfig != null

    constructor() {
        init()
    }

    constructor(sharedContext: EGLContext) {
        this.sharedEglContext = sharedContext
        init()
    }

    private fun init() {
        try {
            eglDisplay = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL.EGL_NO_DISPLAY) {
                Timber.e("get display failed, error = %x", EGL.eglGetError())
                return
            }

            if (!EGL.eglInitialize(eglDisplay, null, 0, null, 0)) {
                Timber.e("init display failed, error = %x", EGL.eglGetError())
                return
            }
            var configArray = Array<EGLConfig?>(20) {
                null
            }
            var numConfigArray = IntArray(1) {
                0
            }
            if (!EGL.eglChooseConfig(eglDisplay, EGL_CONFIG_ATTRS, 0, configArray, 0, 20, numConfigArray, 0)) {
                Timber.e("chooseConfig failed, error = %x", EGL.eglGetError())
                return
            }

            if (numConfigArray[0] > 0) {
                Timber.d("egl config count = %d", numConfigArray[0])
                eglConfig = configArray[0]
            } else {
                Timber.e("egl config count = 0, error = %x", EGL.eglGetError())
                return
            }

            eglContext = EGL.eglCreateContext(eglDisplay, eglConfig!!, sharedEglContext, EGL_CONTEXT_ATTRS, 0)
            if (eglContext == EGL.EGL_NO_CONTEXT) {
                Timber.e("create context failed, sharedContext = $sharedEglContext, error = %s", EGL.eglGetError());
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e("init failed")
        }

    }

    fun makeCurrent(surface: OutputSurface) {
        if (!isReady) {
            return
        }
        if (!EGL.eglMakeCurrent(eglDisplay, surface.eglSurface, surface.eglSurface, eglContext)) {
            Timber.e("makeCurrent failed, error = %x", EGL.eglGetError())
        }
    }

    fun swapBuffers(surface: OutputSurface) {
        if (!isReady) {
            return
        }
        if (!EGL.eglSwapBuffers(eglDisplay, surface.eglSurface)) {
            Timber.e("swapBuffers failed, error = %x", EGL.eglGetError())
        }
    }

    companion object {
        private val EGL_CONFIG_ATTRS = intArrayOf(
            EGL.EGL_BLUE_SIZE, 8,
            EGL.EGL_GREEN_SIZE, 8,
            EGL.EGL_RED_SIZE, 8,
            EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES_BIT,
            EGL.EGL_SURFACE_TYPE, EGL.EGL_WINDOW_BIT,
            EGL.EGL_NONE
        )

        private val EGL_CONTEXT_ATTRS = intArrayOf(
            EGL.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL.EGL_NONE
        )
    }
}