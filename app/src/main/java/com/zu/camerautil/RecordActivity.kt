package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityRecordBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.recorder.CodecRecorder
import com.zu.camerautil.recorder.IRecorder
import com.zu.camerautil.recorder.RecorderParams
import com.zu.camerautil.recorder.SystemRecorder
import com.zu.camerautil.util.createVideoPath
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date

@SuppressLint("MissingPermission")
class RecordActivity : AppCompatActivity() {

    // camera objects start
    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic

    private var openedCameraID: String? = null
    private var currentSize: Size? = null
    private var currentFps: FPS? = null

    private var recordParams: RecorderParams? = null

    private val surfaceStateListener = object : PreviewViewImplementation.SurfaceStateListener {
        override fun onSurfaceCreated(surface: Surface) {
            Timber.d("surfaceCreated: Thread = ${Thread.currentThread().name}")
            binding.cameraLens.setCameras(cameraInfoMap.values)
        }

        override fun onSurfaceSizeChanged(surface: Surface, surfaceWidth: Int, surfaceHeight: Int) {
            val surfaceSize = Size(surfaceWidth, surfaceHeight)
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }
    // camera objects end

    private var recorder: IRecorder = CodecRecorder()
    private var recording = false
        set(value) {
            field = value
            binding.btnRecord.text = if (value) "停止录制" else "开始录制"
            binding.cameraLens.setEnable(!value)
        }
    private lateinit var binding: ActivityRecordBinding

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordBinding.inflate(layoutInflater)
        handlerThread = HandlerThread("RecordActivity-capture")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        setContentView(binding.root)
        initCameraLogic()
        initViews()
    }

    override fun onDestroy() {
        handlerThread.quitSafely()
        cameraLogic.closeCamera()
        recorder.release()
        super.onDestroy()
    }

    private fun initCameraLogic() {
        cameraLogic = BaseCameraLogic(this)
        cameraLogic.configCallback = object : BaseCameraLogic.ConfigCallback {
            override fun getFps(): FPS {
                return currentFps!!
            }

            override fun getSize(): Size {
                return currentSize!!
            }

            override fun getTemplate(): Int {
                return if (recording) {
                    CameraDevice.TEMPLATE_RECORD
                } else {
                    CameraDevice.TEMPLATE_PREVIEW
                }
            }

            override fun getSessionSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                if (recorder.isReady) {
                    surfaceList.add(recorder.getSurface()!!)
                }
                return surfaceList
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                if (recorder.isReady) {
                    surfaceList.add(recorder.getSurface()!!)
                }
                return surfaceList
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                binding.cameraParams.getParamValue(CameraParamID.WB_MODE)?.let {
                    requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, it as Int)
                }
            }
        }

        cameraLogic.cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                openedCameraID = camera.id
            }

            override fun onDisconnected(camera: CameraDevice) {
                openedCameraID = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                openedCameraID = null
            }
        }

        cameraLogic.captureCallback = object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (binding.cameraParams.isParamAuto(CameraParamID.SEC)) {
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { sec ->
                        runOnUiThread {
                            binding.cameraParams.setParamValue(CameraParamID.SEC, sec)
                        }
                    }
                }

                if (binding.cameraParams.isParamAuto(CameraParamID.ISO)) {
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { iso ->
                        runOnUiThread {
                            binding.cameraParams.setParamValue(CameraParamID.ISO, iso)
                        }
                    }
                }
            }

            override fun onCaptureBufferLost(
                session: CameraCaptureSession,
                request: CaptureRequest,
                target: Surface,
                frameNumber: Long
            ) {
                Timber.w("onCaptureBufferLost: target = %s, frameNumber = %s", target.toString(), frameNumber)
            }
        }
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.btnRecord.setOnClickListener {
            if (!recording) {
                startRecord()
            } else {
                stopRecord()
            }
        }


        binding.cameraLens.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false

            if (camera.cameraID != openedCameraID) {
                reopenCamera = true
            }

            if (size != currentSize) {
                currentSize = size
                binding.surfaceMain.previewSize = size
                reopenCamera = true
            }

            if (fps != currentFps) {
                currentFps = fps
                reopenCamera = true
            }

            if (reopenCamera) {
                // 注意这里回调本来就是在主线程内，但是为什么还要post呢？
                // 因为上面PreviewView设置分辨率后，会调用一次requestLayout或者postInvalidate进行重新布局。
                // 但是重新布局并不会立刻进行而是排进主线程队列里。如果这里直接打开相机，就会导致相机输出时PreviewView
                // 并没有被设置为正确的布局，所以这里把打开相机也post到主线程的队列里并且保证它在重布局PreviewView
                // 的后面
                binding.root.post {
                    cameraLogic.closeCamera()
                    cameraLogic.openCamera(camera)
                }
            }

            binding.cameraParams.setCameraConfig(camera, size, fps)
        }

        binding.cameraParams.addAutoModeListener(CameraParamID.SEC) { secAuto ->
            val isoAuto = binding.cameraParams.isParamAuto(CameraParamID.ISO)
            if (secAuto != isoAuto) {
                binding.cameraParams.setParamAuto(CameraParamID.ISO, secAuto)
                setSecAndISOAuto(secAuto)
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.SEC) {
            if (!binding.cameraParams.isParamAuto(CameraParamID.SEC)) {
                val sec = it as? Long ?: return@addValueListener
                val rational = Rational(1_000_000_000, sec.toInt())
                Timber.d("secChange: $rational")
                cameraLogic.updateCaptureRequestParams { builder ->
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sec)
                }
            }
        }

        binding.cameraParams.addAutoModeListener(CameraParamID.ISO) { isoAuto ->
            val secAuto = binding.cameraParams.isParamAuto(CameraParamID.SEC)
            if (secAuto != isoAuto) {
                binding.cameraParams.setParamAuto(CameraParamID.SEC, isoAuto)
                setSecAndISOAuto(isoAuto)
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.ISO) {
            if (!binding.cameraParams.isParamAuto(CameraParamID.ISO)) {
                val iso = it as? Int ?: return@addValueListener
                cameraLogic.updateCaptureRequestParams { builder ->
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                }
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.WB_MODE) { mode ->
            val wbMode = mode as? Int ?: return@addValueListener
            val isManual = mode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
            cameraLogic.updateCaptureRequestParams { requestBuilder ->
                requestBuilder.apply {
                    set(CaptureRequest.CONTROL_AWB_MODE, wbMode)
                }
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.FLASH_MODE) { mode ->
            val flashMode = mode as? FlashUtil.FlashMode ?: return@addValueListener
            cameraLogic.updateCaptureRequestParams { builder ->
                setFlashMode(flashMode, builder)
            }
        }

    }

    private fun setFlashMode(flashMode: FlashUtil.FlashMode, builder: CaptureRequest.Builder) {
        when (flashMode) {
            FlashUtil.FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashUtil.FlashMode.ON -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashUtil.FlashMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashUtil.FlashMode.TORCH -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
        }
    }

    private fun setSecAndISOAuto(auto: Boolean) {
        if (auto) {
            cameraLogic.updateCaptureRequestParams { builder ->
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
        } else {
            cameraLogic.updateCaptureRequestParams { builder ->
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            }
        }
    }

    private fun startRecord() {
        val size = currentSize ?: return
        val fps = currentFps ?: return
        val camera = openedCameraID?.let {
            cameraInfoMap[it] ?: return
        } ?: return
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis()))
        val title = "${size.width}x${size.height}.${fps.value}FPS.$timeStamp.mp4"

        val saveUri = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            com.zu.camerautil.util.createVideoUri(this, title) ?: kotlin.run {
                Timber.e("create uri failed")
                recording = false
                return
            }
        } else {
            null
        }

        val savePath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            createVideoPath(title)
        } else {
            null
        }


        val params = RecorderParams(
            title = title,
            resolution = size,
            inputFps = fps.value,
            outputFps = fps.value,
            sampleRate = 44100,
            outputPath = savePath,
            outputUri = saveUri,
            viewOrientation = binding.root.display.rotation * 90,
            sensorOrientation = camera.sensorOrientation!!,
            facing = cameraInfoMap[openedCameraID!!]!!.lensFacing
        )

        if (!recorder.prepare(params)) {
            Timber.e("recorder prepare failed")
            return
        }
        recordParams = params

        cameraLogic.closeSession()
        recording = recorder.start()
        cameraLogic.createSession()

    }

    private fun stopRecord() {
        val previousParams = recordParams
        cameraLogic.closeSession()
        recorder.stop()
        recorder.release()
        recording = false
        cameraLogic.createSession()
        previousParams?.let { params ->
            if (params.outputUri == null) {
                return@let
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(params.outputUri!!, contentValues, null, null)
        }
    }


}