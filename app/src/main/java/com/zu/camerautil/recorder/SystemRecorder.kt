package com.zu.camerautil.recorder

import android.content.Context
import android.media.MediaCodec
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

    private var mediaRecorder: MediaRecorder? = null


    override fun prepare(params: RecorderParams): Boolean {
        if (mediaRecorder != null) {
            return false
        }
        mediaRecorder = MediaRecorder()
        mediaRecorder?.apply {
//            setAudioSource(AudioSource.MIC)
            setVideoSource(VideoSource.SURFACE)
            setOutputFormat(OutputFormat.MPEG_4)

//            setAudioSamplingRate(params.sampleRate)
//            setAudioChannels(2)
//            setAudioEncoder(AudioEncoder.AAC)

//            setVideoFrameRate(params.fps)
//            if (params.fps != params.captureFps) {
//                setCaptureRate(params.captureFps.toDouble())
//            }

            setVideoEncoder(VideoEncoder.HEVC)

            setOrientationHint(90)

            setVideoSize(params.resolution.width, params.resolution.height)

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
            return true
        } ?: return false
    }

    override fun stop() {
        mediaRecorder?.stop()
    }

    override fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
}