package com.zu.camerautil.recorder.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.zu.camerautil.recorder.RecorderParams
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author zuguorui
 * @date 2024/6/5
 * @description
 */
class AudioEncoder: BaseEncoder("AudioEncoder") {

    override fun prepare(params: RecorderParams): Boolean = synchronized(lockObj) {
        val callback = this.callback ?: return false
        if (state != EncoderState.IDLE) {
            return false
        }
        val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mimeType, params.sampleRate, 2)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, params.audioBufferSize)
        val bitrate = computeAudioBitRate(params.sampleRate, 2, 8)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        try {
            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            encoder = null
            e.printStackTrace()
            callback.onError()
            return false
        }
        state = EncoderState.PREPARED
        return true
    }

    fun feed(data: ByteArray, offset: Int, count: Int) {
        if (state != EncoderState.STARTED) {
            return
        }
        val encoder = encoder ?: return
        val inputBufferId = encoder.dequeueInputBuffer(1000)
        if (inputBufferId < 0) {
            return
        }
        encoder.getInputBuffer(inputBufferId)
        val inputBuffer = encoder.getInputBuffer(inputBufferId) ?: return
        if (inputBuffer.capacity() < count) {
            Timber.e("feed: inputBuffer is smaller than data size")
            encoder.queueInputBuffer(inputBufferId, 0, 0, 0L, 0)
            return
        }
        inputBuffer.rewind()
        inputBuffer.put(data, offset, count)
        encoder.queueInputBuffer(inputBufferId, 0, count, 0, 0)
    }

    override fun signalEndOfStream() {
        val encoder = encoder ?: return
        var retryTimes = 0
        var inputBufferId = -1
        while (retryTimes < 10) {
            inputBufferId = encoder.dequeueInputBuffer(10000)
            if (inputBufferId >= 0) {
                encoder.getInputBuffer(inputBufferId)
                encoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            retryTimes++
        }
        Timber.e("signalEndOfStream failed")
    }

    private fun computeAudioBitRate(sampleRate: Int, channel: Int, depth: Int): Int {
        return 320 * 1000
    }


}