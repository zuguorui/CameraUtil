package com.zu.camerautil.recorder

import android.media.EncoderProfiles
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioEncoder
import android.media.MediaRecorder.AudioSource
import android.media.MediaRecorder.OutputFormat
import android.media.MediaRecorder.VideoEncoder
import android.media.MediaRecorder.VideoSource
import android.view.Surface

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
            //setAudioEncodingBitRate(computeAudioBitRate(params.sampleRate, 2, 16))

            setVideoFrameRate(params.outputFps)
            if (params.outputFps != params.inputFps) {
                setCaptureRate(params.inputFps.toDouble())
            }
            setVideoEncoder(VideoEncoder.HEVC)
            setOrientationHint(90)
            setVideoSize(params.resolution.width, params.resolution.height)
            // setVideoEncodingBitRate(computeVideoBitRate(params.resolution.width, params.resolution.height, params.outputFps, 8))

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

    private fun computeVideoBitRate(width: Int, height: Int, frameRate: Int, depth: Int): Int {
        return width * height * 3 * depth * frameRate
    }

    private fun computeAudioBitRate(sampleRate: Int, channel: Int, depth: Int): Int {
        return sampleRate * channel * depth
    }
}