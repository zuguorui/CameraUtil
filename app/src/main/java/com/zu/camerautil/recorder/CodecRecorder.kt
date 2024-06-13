package com.zu.camerautil.recorder

import android.annotation.SuppressLint
import android.content.res.AssetFileDescriptor
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
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author zuguorui
 * @date 2024/1/5
 * @description
 */
@SuppressLint("MissingPermission")
class CodecRecorder: IRecorder {
    override val isReady: Boolean
        get() {
            val videoReady = videoEncoder.state == EncoderState.PREPARED || videoEncoder.state == EncoderState.STARTED
            val audioReady = audioEncoder.state == EncoderState.PREPARED || audioEncoder.state == EncoderState.STARTED
            return videoReady && audioReady && muxer != null
        }

    override val isRecording: Boolean
        get() = videoEncoder.state == EncoderState.STARTED && audioEncoder.state == EncoderState.STARTED && muxer != null

    private var videoEncoder = VideoEncoder()
    private var audioEncoder = AudioEncoder()
    private var audioInput = AudioInput()
    private var muxer: MediaMuxer? = null
    private var audioTrack = -1
    private var videoTrack = -1
    private val canMux: Boolean
        get() = videoTrack >= 0 && audioTrack >= 0

    private val isMuxRunning = AtomicBoolean(false)

    private var assetFileDescriptor: AssetFileDescriptor? = null

    private var videoStartPts = -1L
    private var audioStartPts = -1L

    private var videoFormatChangeCount = 0
    private var audioFormatChangeCount = 0
    private var lastVideoPts = -1L
    private val videoCallback = object : EncoderCallback() {
        override fun onOutputFormatChanged(format: MediaFormat) {
            Timber.d("video onOutputFormatChanged, count: $videoFormatChangeCount")
            videoFormatChangeCount++
            lastVideoPts = -1L
            if (muxer != null && videoTrack < 0) {
                videoTrack = muxer!!.addTrack(format)
                if (canMux) {
                    startMux()
                }
            }
        }

        override fun onOutputBufferAvailable(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            //Timber.d("video onBufferAvailable")
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                Timber.w("video buffer: codec config")
                return
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                Timber.d("video buffer: key frame")
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_PARTIAL_FRAME != 0) {
                Timber.d("video buffer: partial frame")
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Timber.w("video buffer: end of stream")
            }
            if (lastVideoPts != -1L) {
                val cost = (info.presentationTimeUs - lastVideoPts) / 1000
                Timber.d("frame interval = $cost ms")
            }
            lastVideoPts = info.presentationTimeUs
            if (muxer != null && isMuxRunning.get() && info.size > 0) {
                handleVideoPts(info)
                Timber.d("video pts: ${info.presentationTimeUs / 1000}ms")
                muxer!!.writeSampleData(videoTrack, buffer, info)
            } else {
                Timber.w("don't write video frame")
            }
        }
    }

    private val audioCallback = object : EncoderCallback() {
        override fun onOutputFormatChanged(format: MediaFormat) {
            Timber.d("audio onOutputFormatChanged, count: $audioFormatChangeCount")
            if (muxer != null && audioTrack < 0) {
                audioTrack = muxer!!.addTrack(format)
                if (canMux) {
                    startMux()
                }
            }
        }

        override fun onOutputBufferAvailable(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            //Timber.d("audio onBufferAvailable")
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                Timber.w("audio buffer: codec config")
                return
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                Timber.d("audio buffer: key frame")
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_PARTIAL_FRAME != 0) {
                Timber.d("audio buffer: partial frame")
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Timber.w("audio buffer: end of stream")
            }

            if (muxer != null && isMuxRunning.get() && info.size > 0) {
                handleAudioPts(info)
                //Timber.d("audio pts: ${info.presentationTimeUs / 1000}ms")
                muxer!!.writeSampleData(audioTrack, buffer, info)
            }
        }
    }

    private val audioDataCallback = { data: ByteArray, offset: Int, size: Int ->
        audioEncoder?.feed(data, offset, size)
    }

    init {
        videoEncoder.callback = videoCallback
        audioEncoder.callback = audioCallback
        audioInput.dataCallback = audioDataCallback

    }


    override fun prepare(params: RecorderParams): Boolean {
        if (muxer != null) {
            return false
        }
        if (!videoEncoder.prepare(params)) {
            return false
        }
        if (!audioEncoder.prepare(params)) {
            return false
        }

        if (!audioInput.prepare(params)) {
            return false
        }
        audioInput.dataCallback = audioDataCallback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && params.outputUri != null) {
            assetFileDescriptor = MyApplication.context.contentResolver.openAssetFileDescriptor(params.outputUri!!, "rw") ?: return false
            val fileDescriptor = assetFileDescriptor?.fileDescriptor ?: return false
            muxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else if (params.outputPath != null){
            muxer = MediaMuxer(params.outputPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            return false
        }

        val orientation = computeRotation(params.sensorOrientation, params.viewOrientation, params.facing)
        muxer?.setOrientationHint(orientation)
        isMuxRunning.set(false)

        audioTrack = -1
        videoTrack = -1
        videoFormatChangeCount = 0
        audioFormatChangeCount = 0
        videoStartPts = -1L
        audioStartPts = -1L

        return true
    }

    override fun getSurface(): Surface? {
        return videoEncoder.surface
    }

    override fun start(): Boolean {
        if (canMux) {
            startMux()
        }
        videoEncoder.start()
        audioEncoder.start()
        audioInput.start()
        return true
    }

    override fun stop() {
        audioInput.stop()
        audioEncoder.stop()
        videoEncoder.stop()

        isMuxRunning.set(false)
        muxer?.stop()
        muxer?.release()
        muxer = null
        audioTrack = -1
        videoTrack = -1
        closeFileDescriptor()
    }

    override fun release() {
        audioInput.release()
        audioEncoder.release()
        videoEncoder.release()

        isMuxRunning.set(false)
        muxer?.stop()
        muxer?.release()
        muxer = null
        audioTrack = -1
        videoTrack = -1
    }

    private fun startMux() {
        muxer?.start()
        isMuxRunning.set(true)
    }

    private fun closeFileDescriptor() {
        assetFileDescriptor?.close()
    }

    private fun handleAudioPts(info: MediaCodec.BufferInfo) {
        if (audioStartPts == -1L) {
            audioStartPts = info.presentationTimeUs
        }
        info.presentationTimeUs -= audioStartPts
    }

    private fun handleVideoPts(info: MediaCodec.BufferInfo) {
        if (videoStartPts == -1L) {
            videoStartPts = info.presentationTimeUs
        }
        info.presentationTimeUs -= videoStartPts
    }
}