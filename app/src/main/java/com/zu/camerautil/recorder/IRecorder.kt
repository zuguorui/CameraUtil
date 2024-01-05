package com.zu.camerautil.recorder

import android.view.Surface

/**
 * @author zuguorui
 * @date 2024/1/5
 * @description
 */
interface IRecorder {
    fun prepare(params: RecorderParams): Boolean
    fun getSurface(): Surface?
    fun start(): Boolean
    fun stop()
    fun release()
}