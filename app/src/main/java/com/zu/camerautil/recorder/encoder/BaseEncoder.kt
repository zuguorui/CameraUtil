package com.zu.camerautil.recorder.encoder

import android.media.MediaCodec
import com.zu.camerautil.recorder.RecorderParams
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author zuguorui
 * @date 2024/6/6
 * @description
 */
abstract class BaseEncoder(val name: String) {

    var state = EncoderState.IDLE
        protected set

    protected var encoder: MediaCodec? = null

    protected var callback: EncoderCallback? = null

    protected var encodeThread: Thread? = null

    protected var stopFlag = AtomicBoolean(false)

    abstract fun prepare(params: RecorderParams, callback: EncoderCallback): Boolean

    /**
     * 开始编码。
     * 必须在state为[EncoderState.READY]时调用才会成功。
     * 调用成功后，state切换为[EncoderState.RUNNING]
     * */
    @Synchronized
    open fun start() {
        if (state != EncoderState.READY) {
            return
        }
        startEncodeThread()
    }

    /**
     * 停止编码。
     * 必须在state为[EncoderState.RUNNING]时调用才会成功。
     * 调用成功后，state切换为[EncoderState.READY]。
     * 可以再次调用[start]开始编码。
     * */
    @Synchronized
    open fun stop() {
        if (state != EncoderState.RUNNING) {
            return
        }
        stopEncodeThread()
    }


    /**
     * 结束编码。
     * 必须在编码器已经运行时才会起作用。结束之后，该编码器不再可用，
     * state切换为[EncoderState.IDLE]，必须重新prepare。
     * */
    @Synchronized
    open fun finish() {
        if (state != EncoderState.RUNNING) {
            return
        }
        encoder?.signalEndOfInputStream()
    }

    /**
     * 释放编码器。
     * 如果有正在进行的编码，将会结束编码。
     * 释放之后，state切换为[EncoderState.IDLE]
     * */
    @Synchronized
    open fun release() {
        finish()
        encodeThread?.join()
        encodeThread = null
        state = EncoderState.IDLE
    }

    protected open fun startEncodeThread() {
        if (stopFlag.get()) {
            encodeThread?.join()
            encodeThread = null
        }
        if (encodeThread != null) {
            return
        }
        stopFlag.set(false)
        encodeThread = Thread(this::encodeLoop, "$name-thread").apply {
            start()
        }
    }

    protected open fun stopEncodeThread() {
        stopFlag.set(true)
        encodeThread?.join()
        encodeThread = null
    }

    protected open fun encodeLoop() {
        val encoder = encoder ?: return
        val callback = callback ?: return
        var outputBufferId: Int = 0
        var outputBufferInfo = MediaCodec.BufferInfo()
        var outputBuffer: ByteBuffer? = null
        var isEof = false
        encoder.start()
        state = EncoderState.RUNNING
        callback?.onStart()
        while (!stopFlag.get()) {
            outputBufferId = encoder.dequeueOutputBuffer(outputBufferInfo, 5000)
            if (outputBufferId >= 0) {
                if (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEof = true
                    stopFlag.set(true)
                }
                outputBuffer = encoder.getOutputBuffer(outputBufferId)
                callback.onOutputBufferAvailable(outputBuffer!!, outputBufferInfo)
                encoder.releaseOutputBuffer(outputBufferId, false)
                outputBuffer = null
            } else if (outputBufferId and MediaCodec.INFO_OUTPUT_FORMAT_CHANGED != 0) {
                callback?.onOutputFormatChanged(encoder.outputFormat)
            }
        }
        if (isEof) {
            encoder.release()
            this.encoder = null
            state = EncoderState.IDLE
            callback?.onFinish()
        } else {
            encoder.stop()
            state = EncoderState.READY
            callback?.onStop()
        }
    }
}