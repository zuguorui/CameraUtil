package com.zu.camerautil.recorder.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import com.zu.camerautil.recorder.RecorderParams
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author zuguorui
 * @date 2024/6/5
 * @description
 */
class VideoEncoder: BaseEncoder("VideoEncoder") {
    var surface: Surface? = null
        private set

    override fun prepare(params: RecorderParams, callback: EncoderCallback): Boolean {
        this.callback = callback
        if (state != EncoderState.IDLE) {
            return false
        }
        val useHEVC = supportHEVC() && params.inputFps >= 120
        val mimeType = if (useHEVC) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        val width = params.resolution.width
        val height = params.resolution.height

        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

        val bitrate = computeVideoBitRate(width, height, params.outputFps, 8)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, params.outputFps)
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, params.inputFps)
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        try {
            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder?.createInputSurface()
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError()
            return false
        }
        state = EncoderState.READY
        return true
    }



    private fun supportHEVC(): Boolean {
        val allCodec = MediaCodecList(MediaCodecList.ALL_CODECS)
        allCodec.codecInfos.forEach {
            if (it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                return true
            }
        }
        return false
    }

    // 计算视频比特率
    // https://zidivo.com/blog/video-bitrate-guide/
    private val videoQuality = 1
    private fun computeVideoBitRate(width: Int, height: Int, frameRate: Int, pixelSize: Int): Int {
        return (0.07 * width * height * pixelSize * frameRate * videoQuality).toInt()
    }


}