package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityWbBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.view.CameraSpinnerAdapter
import timber.log.Timber
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class WbActivity : AppCompatActivity() {

    // camera objects start
    private val cameraManager: CameraManager by lazy {
        ((getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private var surfaceCreated = false

    private var openedCameraID: String? = null

    private var currentSize: Size? = null
    private var currentFps: FPS? = null

    private var camera: CameraDevice? = null

    private var session: CameraCaptureSession? = null
    private var highSpeedSession: CameraConstrainedHighSpeedCaptureSession? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private var cameraThread = HandlerThread("CameraThread").apply { start() }

    private var cameraHandler = Handler(cameraThread.looper)

    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

    }


    private val surfaceStateListener = object : PreviewViewImplementation.SurfaceStateListener {
        override fun onSurfaceCreated(surface: Surface) {
            Timber.d("surfaceCreated: Thread = ${Thread.currentThread().name}")
            surfaceCreated = true
            binding.cameraSelector.setCameras(cameraInfoMap.values)
//            currentSize = binding.cameraSelector.currentSize
//            currentFps = binding.cameraSelector.currentFps

        }

        override fun onSurfaceSizeChanged(surface: Surface, width: Int, height: Int) {
            val surfaceSize = binding.surfaceMain.surfaceSize
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }
    // camera objects end

    private lateinit var binding: ActivityWbBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWbBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
//            if (camera.cameraID != openedCameraID) {
//                closeDevice()
//                openDevice(camera.cameraID)
//            } else if (size != currentSize || fps != currentFps) {
//                closeSession()
//                createSession()
//            }

            if (camera.cameraID != openedCameraID || size != currentSize || fps != currentFps) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
                closeDevice()
                openDevice(camera.cameraID)
            }
        }
    }


    @Synchronized
    private fun openDevice(cameraID: String) {
        if (openedCameraID == cameraID) {
            Timber.w("same cameraID, return")
        }
        if (camera != null) {
            closeDevice()
        }
        openedCameraID = cameraID

        // 获取最接近容器宽高比的Camera输出，并且设置给SurfaceView
        binding.surfaceMain.previewSize = binding.cameraSelector.currentSize
        currentSize = binding.cameraSelector.currentSize

        val info = cameraInfoMap[openedCameraID]!!
        val finalID = if (info.isInCameraIdList) {
            info.cameraID
        } else {
            if (StaticConfig.specifyCameraMethod == SpecifyCameraMethod.IN_CONFIGURATION) {
                info.logicalID ?: info.cameraID
            } else {
                info.cameraID
            }
        }
        Timber.d("openDevice $finalID")
        cameraManager.openCamera(finalID, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@WbActivity.camera = camera
                createSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Timber.e("open camera failed")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraID error: ($error) $msg")
                Timber.e(exc.message, exc)
            }
        }, cameraHandler)
    }

    private fun closeDevice() {
        closeSession()
        camera?.close()
        camera = null
        openedCameraID = null
    }

    private fun createSession() {
        val camera = this.camera ?: return

        val createSessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(newSession: CameraCaptureSession) {
                if (binding.cameraSelector.currentFps.type == FPS.Type.HIGH_SPEED) {
                    highSpeedSession = newSession as CameraConstrainedHighSpeedCaptureSession
                } else {
                    session = newSession
                }
                startPreview()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exception = RuntimeException("create session failed")
                Timber.e("onConfigureFailed: ${exception.message}, session: $session")
            }
        }

        currentFps = binding.cameraSelector.currentFps

        val isHighSpeed = binding.cameraSelector.currentFps.type == FPS.Type.HIGH_SPEED

        val target = getSessionSurfaceList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = cameraInfoMap[openedCameraID]!!
            val outputConfigurations = ArrayList<OutputConfiguration>()
            for (surface in target) {
                val outputConfiguration = OutputConfiguration(surface)
                if (!isHighSpeed && info.logicalID != null && !info.isInCameraIdList && StaticConfig.specifyCameraMethod == SpecifyCameraMethod.IN_CONFIGURATION) {
                    Timber.w("camera${info.cameraID} belong to logical camera${info.logicalID}, set physical camera")
                    outputConfiguration.setPhysicalCameraId(info.cameraID)
                }
                outputConfigurations.add(outputConfiguration)
            }
            val sessionType = if (isHighSpeed) {
                SessionConfiguration.SESSION_HIGH_SPEED
            } else {
                SessionConfiguration.SESSION_REGULAR
            }

            camera.createCaptureSession(SessionConfiguration(sessionType, outputConfigurations, cameraExecutor, createSessionCallback))

        } else {
            camera.createCaptureSession(target, createSessionCallback, cameraHandler)
        }
    }

    private fun closeSession() {
        stopPreview()
        session?.close()
        session = null
        highSpeedSession?.close()
        highSpeedSession = null
    }

    private fun startPreview() {

        val camera = this.camera ?: return

        val fps = binding.cameraSelector.currentFps.value
        val isHighSpeed = binding.cameraSelector.currentFps.type == FPS.Type.HIGH_SPEED

        captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            getCaptureSurfaceList().forEach {
                addTarget(it)
            }
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        }
        if (!isHighSpeed) {
            session?.run {
                setRepeatingRequest(captureRequestBuilder!!.build(), captureCallback, cameraHandler)
            }
        } else {
            if (Build.VERSION.SDK_INT >= 28) {
                highSpeedSession?.run {
                    val highSpeedRequest = createHighSpeedRequestList(captureRequestBuilder!!.build())
                    //setRepeatingBurstRequests(highSpeedRequest, cameraExecutor, captureCallback)
                    setRepeatingBurst(highSpeedRequest, captureCallback, cameraHandler)
                }
            } else {
                Timber.e("SDK ${Build.VERSION.SDK_INT} can't create high speed preview")
            }
        }

    }

    private fun stopPreview() {
        session?.stopRepeating()
        highSpeedSession?.stopRepeating()
    }

    private fun getSessionSurfaceList(): ArrayList<Surface> {
        return arrayListOf(binding.surfaceMain.surface)
    }

    private fun getCaptureSurfaceList(): ArrayList<Surface> {
        return arrayListOf(binding.surfaceMain.surface)
    }
}