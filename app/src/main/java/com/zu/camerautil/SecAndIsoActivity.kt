package com.zu.camerautil

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivitySecAndIsoBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber

@SuppressLint("MissingPermission")
class SecAndIsoActivity : AppCompatActivity() {

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic

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

    private lateinit var binding: ActivitySecAndIsoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecAndIsoBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        val windowInsetsController =
//            WindowCompat.getInsetsController(window, window.decorView)
//        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
//        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        initCameraLogic()
        initViews()
    }

    override fun onDestroy() {
        cameraLogic.closeCamera()
        super.onDestroy()
    }

    private fun initCameraLogic() {
        cameraLogic = BaseCameraLogic(this)
        cameraLogic.configCallback = object : BaseCameraLogic.ConfigCallback {
            override fun getFps(): FPS {
                return currentFps!!
            }

            override fun getSessionSurfaceList(): List<Surface> {
                return arrayListOf(binding.surfaceMain.surface)
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
            }

            override fun getSize(): Size {
                return currentSize!!
            }

            override fun getTemplate(): Int {
                return CameraDevice.TEMPLATE_PREVIEW
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {

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
        }
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.cameraLens.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false
            if (camera.cameraID != openedCameraID) {
                reopenCamera = true
            }

            if (size != currentSize) {
                currentSize = size
                binding.surfaceMain.previewSize = currentSize!!
                reopenCamera = true
            }

            if (fps != currentFps) {
                currentFps = fps
                reopenCamera = true
            }

            if (reopenCamera) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
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