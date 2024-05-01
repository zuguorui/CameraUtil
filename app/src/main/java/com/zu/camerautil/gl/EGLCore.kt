package com.zu.camerautil.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.view.Surface
import timber.log.Timber
import java.lang.Exception


class EGLCore {
    var eglContext: EGLContext = EGL.EGL_NO_CONTEXT
        private set

    var eglDisplay: EGLDisplay = EGL.EGL_NO_DISPLAY
        private set

    var sharedEglContext: EGLContext = EGL.EGL_NO_CONTEXT
        private set

    var eglConfig: EGLConfig? = null

    val isReady: Boolean
        get() = eglContext != EGL.EGL_NO_CONTEXT && eglDisplay != EGL.EGL_NO_DISPLAY && eglConfig != null

    var isReleased = false
        private set

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
            val numConfigs = intArrayOf(0)
            if (!EGL.eglChooseConfig(eglDisplay, EGL_CONFIG_ATTRS, 0, null, 0, 0, numConfigs, 0)) {
                Timber.e("chooseConfig failed, error = %x", EGL.eglGetError())
                return
            }

            if (numConfigs[0] > 0) {
                Timber.d("egl config count = %d", numConfigs[0])
            } else {
                Timber.e("egl config count = 0, error = %x", EGL.eglGetError())
                return
            }

            val configs = Array<EGLConfig?>(numConfigs[0]) {
                null
            }

            if (!EGL.eglChooseConfig(eglDisplay, EGL_CONFIG_ATTRS, 0, configs, 0, numConfigs[0], numConfigs, 0)) {
                Timber.e("egl choose config failed, error = %s", EGL.eglGetError())
                return
            }

            eglConfig = chooseConfig(eglDisplay, configs)
            if (eglConfig == null) {
                Timber.e("choose config failed")
                return
            }

            eglContext = EGL.eglCreateContext(eglDisplay, eglConfig!!, sharedEglContext, EGL_CONTEXT_ATTRS, 0)
            if (eglContext == EGL.EGL_NO_CONTEXT) {
                Timber.e("create context failed, sharedContext = $sharedEglContext, error = %s", EGL.eglGetError());
                return
            }
            EGL.eglMakeCurrent(eglDisplay, EGL.EGL_NO_SURFACE, EGL.EGL_NO_SURFACE, eglContext)
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e("init failed")
        }

    }

    private fun chooseConfig(display: EGLDisplay, configs: Array<EGLConfig?>): EGLConfig? {
        for (config in configs) {
            if (config == null) {
                continue
            }
            val redBits = findConfigAttr(display, config!!, EGL.EGL_RED_SIZE, 0)
            val greenBits = findConfigAttr(display, config!!, EGL.EGL_GREEN_SIZE, 0)
            val blueBits = findConfigAttr(display, config!!, EGL.EGL_BLUE_SIZE, 0)
            if (redBits == 8 && greenBits == 8 && blueBits == 8) {
                return config
            }
        }
        return null
    }

    private val attrTemp = intArrayOf(0)

    private fun findConfigAttr(display: EGLDisplay, config: EGLConfig, attr: Int, defaultValue: Int): Int {
        if (EGL.eglGetConfigAttrib(display, config, attr, attrTemp, 0)) {
            return attrTemp[0]
        }
        return defaultValue
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

    fun release() {
        if (eglContext != null) {
            EGL.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL.EGL_NO_CONTEXT
        }
        if (eglDisplay != EGL.EGL_NO_DISPLAY) {
            EGL.eglMakeCurrent(eglDisplay, EGL.EGL_NO_SURFACE, EGL.EGL_NO_SURFACE, EGL.EGL_NO_CONTEXT)
            eglDisplay = EGL.EGL_NO_DISPLAY
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