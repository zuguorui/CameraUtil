package com.zu.camerautil.recorder

import android.hardware.camera2.CameraCharacteristics
import android.media.EncoderProfiles
import android.media.EncoderProfiles.VideoProfile
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioEncoder
import android.media.MediaRecorder.AudioSource
import android.media.MediaRecorder.OutputFormat
import android.media.MediaRecorder.VideoEncoder
import android.media.MediaRecorder.VideoSource
import android.view.Surface
import androidx.core.graphics.scaleMatrix

/**
 * @author zuguorui
 * @date 2024/1/5
 * @description
 */
class SystemRecorder: IRecorder {

    override val isReady: Boolean
        get() = mediaRecorder?.surface != null
    override val isRecording: Boolean
        get() = _isRecording

    private var _isRecording = false

    private var mediaRecorder: MediaRecorder? = null

    override fun prepare(params: RecorderParams): Boolean {
        if (mediaRecorder != null) {
            return false
        }
        mediaRecorder = MediaRecorder()

        mediaRecorder?.apply {
            setAudioSource(AudioSource.MIC)
            setVideoSource(VideoSource.SURFACE)
            setOutputFormat(OutputFormat.MPEG_4)

            setAudioSamplingRate(params.sampleRate)
            setAudioChannels(2)
            setAudioEncoder(AudioEncoder.AAC)
            setAudioEncodingBitRate(computeAudioBitRate(params.sampleRate, 2, 16))

            setVideoFrameRate(params.outputFps)
            if (params.outputFps != params.inputFps) {
                setCaptureRate(params.inputFps.toDouble())
            }
            if (params.inputFps >= 120) {
                setVideoEncoder(VideoEncoder.HEVC)
            } else {
                setVideoEncoder(VideoEncoder.H264)
            }
            val isFront = params.facing == CameraCharacteristics.LENS_FACING_FRONT
            val rotationSign = if (isFront) 1 else -1
            val rotation = (params.sensorOrientation - params.viewOrientation * rotationSign + 360) % 360
            setOrientationHint(rotation)
            if (isFront) {
                scaleMatrix(-1f, 1f)
            }
            setVideoSize(params.resolution.width, params.resolution.height)
            setVideoEncodingBitRate(computeVideoBitRate(params.resolution.width, params.resolution.height, params.outputFps, 24))

            setOutputFile(params.outputFile.absolutePath)
        }
        mediaRecorder?.prepare()
        return true
    }

    override fun getSurface(): Surface? {
        return mediaRecorder?.surface
    }

    override fun start(): Boolean {
        mediaRecorder?.let {
            it.start()
            _isRecording = true
            return true
        } ?: kotlin.run {
            _isRecording = false
            return false
        }
    }

    override fun stop() {
        mediaRecorder?.stop()
        _isRecording = false
    }

    override fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
        _isRecording = false
    }

    // 计算视频比特率
    // https://zidivo.com/blog/video-bitrate-guide/
    private val videoQuality = 1
    private fun computeVideoBitRate(width: Int, height: Int, frameRate: Int, pixelSize: Int): Int {
        return (0.07 * width * height * pixelSize * frameRate * videoQuality).toInt()
    }

    private fun computeAudioBitRate(sampleRate: Int, channel: Int, depth: Int): Int {
        return 320 * 1000
    }
}