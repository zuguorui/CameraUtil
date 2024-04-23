package com.zu.camerautil.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread

class GLRender(val context: Context) {
    private val glThread = HandlerThread("gl-thread").apply { start() }
    private val glHandler = Handler(glThread.looper)


}