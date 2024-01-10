package com.zu.camerautil.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Camera.CameraInfo
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import com.zu.camerautil.SpecifyCameraMethod
import com.zu.camerautil.StaticConfig
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * @author zuguorui
 * @date 2024/1/9
 * @description 相机基础逻辑类，负责相机的启动、关闭和配置逻辑。具体配置通过[ConfigCallback]由客户端传入
 */

@SuppressLint("MissingPermission")
class BaseCameraLogic(val context: Context) {

    var configCallback: ConfigCallback? = null
    var cameraStateCallback: CameraDevice.StateCallback? = null
    var sessionStateCallback: CameraCaptureSession.StateCallback? = null
    var captureCallback: CameraCaptureSession.CaptureCallback? = null

    private var internalCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            captureCallback?.onCaptureStarted(session, request, timestamp, frameNumber)
        }


        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            captureCallback?.onCaptureProgressed(session, request, partialResult)
        }
    }

    private val cameraManager: CameraManager by lazy {
        ((context.getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    var currentCameraInfo: CameraInfoWrapper? = null
    var currentFps: FPS? = null

    private var camera: CameraDevice? = null

    private var session: CameraCaptureSession? = null
    private var highSpeedSession: CameraConstrainedHighSpeedCaptureSession? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private var cameraThread = HandlerThread("CameraThread").apply { start() }

    private var cameraHandler = Handler(cameraThread.looper)

    private var cameraExecutor = Executors.newSingleThreadExecutor()

    /**
     * 启动相机
     * */
    fun openDevice(cameraInfo: CameraInfoWrapper) {
        val configCallback = configCallback ?: return

        if (camera != null) {
            closeDevice()
        }

        val finalID = if (cameraInfo.isInCameraIdList) {
            cameraInfo.cameraID
        } else {
            if (StaticConfig.specifyCameraMethod == SpecifyCameraMethod.IN_CONFIGURATION) {
                cameraInfo.logicalID ?: cameraInfo.cameraID
            } else {
                cameraInfo.cameraID
            }
        }
        Timber.d("openDevice $finalID")
        cameraManager.openCamera(finalID, object : CameraDevice.StateCallback() {
            override fun onOpened(pCamera: CameraDevice) {
                cameraStateCallback?.onOpened(pCamera)
                camera = pCamera
                currentCameraInfo = cameraInfo
                createSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraStateCallback?.onDisconnected(camera)
                Timber.e("camera disconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraStateCallback?.onError(camera, error)
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera ${cameraInfo.cameraID} error: ($error) $msg")
                Timber.e(exc.message, exc)
            }
        }, cameraHandler)
    }

    fun closeDevice() {
        closeSession()
        camera?.close()
        camera = null
        currentCameraInfo = null
    }

    fun createSession() {
        val camera = this.camera ?: return
        val configCallback = configCallback ?: return
        currentFps = configCallback.getFps()

        val createSessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(newSession: CameraCaptureSession) {
                sessionStateCallback?.onConfigured(newSession)
                if (currentFps!!.type == FPS.Type.HIGH_SPEED) {
                    highSpeedSession = newSession as CameraConstrainedHighSpeedCaptureSession
                } else {
                    session = newSession
                }
                Timber.w("sessionConfigured")
                startPreview()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                sessionStateCallback?.onConfigureFailed(session)
                val exception = RuntimeException("create session failed")
                Timber.e("onConfigureFailed: ${exception.message}, session: $session")
            }
        }


        val isHighSpeed = currentFps!!.type == FPS.Type.HIGH_SPEED

        val target = configCallback.getSessionSurfaceList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = currentCameraInfo!!
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

    fun closeSession() {
        stopPreview()
        session?.close()
        session = null
        highSpeedSession?.close()
        highSpeedSession = null
    }

    fun startPreview() {
        configRequestBuilder()
        startRepeating()
    }

    private fun configRequestBuilder() {
        val camera = this.camera ?: return
        val configCallback = configCallback ?: return

        val fps = currentFps?.value ?: return

        captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            configCallback.getCaptureSurfaceList().forEach {
                addTarget(it)
            }
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        }

        // 给客户端有机会做自定义配置
        configCallback?.configBuilder(captureRequestBuilder!!)
    }

    private fun startRepeating() {
        val isHighSpeed = currentFps?.type?.let {
            it == FPS.Type.HIGH_SPEED
        } ?: return
        Timber.w("startRepeating, isHighSpeed = $isHighSpeed")
        if (!isHighSpeed) {
            session?.run {
                setRepeatingRequest(captureRequestBuilder!!.build(), internalCaptureCallback, cameraHandler)
            }
        } else {
            if (Build.VERSION.SDK_INT >= 28) {
                highSpeedSession?.run {
                    val highSpeedRequest = createHighSpeedRequestList(captureRequestBuilder!!.build())
                    setRepeatingBurstRequests(highSpeedRequest, cameraExecutor, internalCaptureCallback)
                    //setRepeatingBurst(highSpeedRequest, internalCaptureCallback, cameraHandler)
                }
            } else {
                Timber.e("SDK ${Build.VERSION.SDK_INT} can't create high speed preview")
            }
        }
    }

    fun stopPreview() {
        session?.stopRepeating()
        highSpeedSession?.stopRepeating()
    }

    /**
     * 更新一些参数到session，并且不会重启相机和session。
     * 调用该方法后，会通过[ConfigCallback.configBuilder]将[CaptureRequest.Builder]
     * 传给客户端，由客户端进行自定义配置，然后更新到session。
     * */
    fun updateCaptureRequestParams() {
        val builder = captureRequestBuilder ?: return
        val configCallback = configCallback ?: return
        configCallback.configBuilder(builder)
        startRepeating()
    }

    /**
     * 相机配置回调，在相应时刻，会向客户端请求配置。
     * */
    interface ConfigCallback {
        fun getFps(): FPS
        fun getSessionSurfaceList(): List<Surface>
        fun getCaptureSurfaceList(): List<Surface>
        fun configBuilder(requestBuilder: CaptureRequest.Builder)
    }
}