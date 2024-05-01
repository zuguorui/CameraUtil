package com.zu.camerautil

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.CameraUsage
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityGlBinding
import com.zu.camerautil.gl.GLRender
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.recorder.RecorderParams
import timber.log.Timber

class GLActivity : AppCompatActivity() {

    // camera objects start

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic
    private lateinit var glRender: GLRender

    private var openedCameraID: String? = null
    private var currentSize: Size? = null
    private var currentFps: FPS? = null

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

    private val surfaceListener = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.d("surfaceCreated")

        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            glRender = GLRender(this@GLActivity)
            glRender.addOutputSurface(holder.surface, width, height)
            val surfaceSize = Size(width, height)
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
            glRender.inputSurfaceListener = object : GLRender.InputSurfaceListener {
                override fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
                    Timber.d("gl surface created, width = $width, height = $height")
                    runOnUiThread {
                        Timber.w("thread = ${Thread.currentThread().name}")
                        binding.cameraLens.setCameras(cameraInfoMap.values)
                    }
                }

                override fun onSizeChanged(surface: Surface, width: Int, height: Int) {
                    Timber.d("gl surface size changed, width = $width, height = $height")
                }
            }

        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            glRender.removeOutputSurface(holder.surface)
        }
    }

    private lateinit var binding: ActivityGlBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initCameraLogic()
        initViews()
    }

    override fun onDestroy() {
        cameraLogic.closeCamera()
        glRender.release()
        super.onDestroy()
    }

    private fun initCameraLogic() {
        cameraLogic = BaseCameraLogic(this)
        cameraLogic.configCallback = object : BaseCameraLogic.ConfigCallback {
            override fun getFps(): FPS {
                return currentFps!!
            }

            override fun getSize(): Size {
                val size = currentSize!!
                glRender.changeInputSize(size.width, size.height)
                return size
            }

            override fun getUsage(): CameraUsage {
                return CameraUsage.PREVIEW
            }

            override fun getSessionSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(glRender.inputSurface!!.surface)
                return surfaceList
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
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
        binding.surface.holder.addCallback(surfaceListener)

        binding.cameraLens.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false

            if (camera.cameraID != openedCameraID) {
                reopenCamera = true
            }

            if (size != currentSize) {
                currentSize = size
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
}