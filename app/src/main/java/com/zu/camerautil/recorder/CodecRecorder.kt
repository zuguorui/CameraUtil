package com.zu.camerautil.recorder

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import com.zu.camerautil.MyApplication
import com.zu.camerautil.camera.computeRotation
import com.zu.camerautil.recorder.encoder.AudioEncoder
import com.zu.camerautil.recorder.encoder.EncoderCallback
import com.zu.camerautil.recorder.encoder.EncoderState
import com.zu.camerautil.recorder.encoder.VideoEncoder
import java.nio.ByteBuffer

/**
 * @author zuguorui
 * @date 2024/1/5
 * @description
 */
@SuppressLint("MissingPermission")
class CodecRecorder: IRecorder {
    override val isReady: Boolean
        get() = videoEncoder.state == EncoderState.READY && audioEncoder.state == EncoderState.READY && muxer != null

    override val isRecording: Boolean
        get() = videoEncoder.state == EncoderState.RUNNING && audioEncoder.state == EncoderState.RUNNING && muxer != null

    private var videoEncoder = VideoEncoder()
    private var audioEncoder = AudioEncoder()
    private var muxer: MediaMuxer? = null
    private var audioInput = AudioInput()
    private var audioTrack = -1
    private var videoTrack = -1

    private val videoCallback = object : EncoderCallback() {
        override fun onOutputFormatChanged(format: MediaFormat) {
            if (muxer != null) {
                audioTrack = muxer!!.addTrack(format)
            }
        }

        override fun onOutputBufferAvailable(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (muxer != null) {
                muxer!!.writeSampleData(audioTrack, buffer, info)
            }
        }
    }

    private val audioCallback = object : EncoderCallback() {
        override fun onOutputFormatChanged(format: MediaFormat) {
            if (muxer != null) {
                videoTrack = muxer!!.addTrack(format)
            }
        }

        override fun onOutputBufferAvailable(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (muxer != null) {
                muxer!!.writeSampleData(videoTrack, buffer, info)
            }
        }
    }

    private val audioDataCallback = { data: ByteArray, offset: Int, size: Int ->

    }


    override fun prepare(params: RecorderParams): Boolean {
        if (muxer != null) {
            return false
        }
        if (!videoEncoder.prepare(params, videoCallback)) {
            return false
        }
        if (!audioEncoder.prepare(params, audioCallback)) {
            return false
        }

        if (!audioInput.prepare(params)) {
            return false
        }
        audioInput.dataCallback = audioDataCallback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && params.outputUri != null) {
            val fileDescriptor = MyApplication.context.contentResolver.openFileDescriptor(params.outputUri!!, "w")?.fileDescriptor ?: return false
            muxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else if (params.outputPath != null){
            muxer = MediaMuxer(params.outputPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            return false
        }

        val orientation = computeRotation(params.sensorOrientation, params.viewOrientation, params.facing)
        muxer?.setOrientationHint(orientation)
        return true
    }

    override fun getSurface(): Surface? {
        return videoEncoder.surface
    }

    override fun start(): Boolean {
        muxer?.start()
        videoEncoder.start()
        audioEncoder.start()
        return true
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun release() {
        TODO("Not yet implemented")
    }
}