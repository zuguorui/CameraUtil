package com.zu.camerautil.recorder.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.zu.camerautil.recorder.RecorderParams
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author zuguorui
 * @date 2024/6/5
 * @description
 */
class AudioEncoder: BaseEncoder("AudioEncoder") {

    override fun prepare(params: RecorderParams, callback: EncoderCallback): Boolean {
        this.callback = callback
        if (state != EncoderState.IDLE) {
            return false
        }
        val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mimeType, params.sampleRate, 2)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, params.audioBufferSize)
        try {
            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            encoder = null
            e.printStackTrace()
            callback.onError()
            return false
        }
        state = EncoderState.READY
        return true
    }

    fun feed(data: ByteArray, offset: Int, count: Int) {
        if (state != EncoderState.RUNNING || encoder == null) {
            return
        }

    }


}