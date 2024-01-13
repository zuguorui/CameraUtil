package com.zu.camerautil.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Size
import android.view.Surface
import timber.log.Timber

class FakeMediaEncoderSurfaceProvider: IRecorder {

    private var encoder: MediaCodec? = null

    private var surface: Surface? = null

    private var mediaFormat: MediaFormat? = null

    override val isReady: Boolean
        get() = surface != null

    override val isRecording: Boolean = false

    private var _isRecording: Boolean = false
    val size: Size?
        get() = mediaFormat?.run {
            Size(getInteger(MediaFormat.KEY_WIDTH), getInteger(MediaFormat.KEY_HEIGHT))
        }

    override fun prepare(params: RecorderParams): Boolean {
        return prepare(params.resolution, params.inputFps)
    }

    fun prepare(size: Size, fps: Int): Boolean {
        if (encoder != null) {
            return false
        }

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val isHighSpeed = fps >= 120 || fps >= 120
        var mimeStr = if (isHighSpeed) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        mediaFormat = MediaFormat.createVideoFormat(mimeStr, size.width, size.height).apply {
            setString(MediaFormat.KEY_FRAME_RATE, null)
        }
        var encoderName = codecList.findEncoderForFormat(mediaFormat)
        if (encoderName == null) {
            if (isHighSpeed) {
                mimeStr = MediaFormat.MIMETYPE_VIDEO_AVC
                mediaFormat!!.setString(MediaFormat.KEY_MIME, mimeStr)
                encoderName = codecList.findEncoderForFormat(mediaFormat)
            }
            if (encoderName == null) {
                Timber.e("failed to get encoder for size: $size, fps: $fps")
                return false
            }
        }

        encoder = MediaCodec.createByCodecName(encoderName)
        encoder?.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                if (!_isRecording) {
                    return
                }
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Timber.e("encoder onError: $e")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Timber.w("encoder onOutputFormatChanged")
            }
        })

        mediaFormat?.apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_BIT_RATE, 50 * 1000 * 1000 * 8)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f)
            if (mimeStr == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4)
            } else {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            }
        }

        encoder?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = encoder!!.createInputSurface()
        _isRecording = true
        return true
    }
    override fun getSurface(): Surface? {
        return surface
    }

    override fun start(): Boolean {
        encoder?.let {
            it.start()
            return true
        }
        return true
    }

    override fun stop() {
        _isRecording = false
        encoder?.stop()
        release()
    }

    override fun release() {
        _isRecording = false
        encoder?.release()
        encoder = null
        surface = null
        mediaFormat = null
    }
}