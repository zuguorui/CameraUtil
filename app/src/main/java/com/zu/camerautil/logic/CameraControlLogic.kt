package com.zu.camerautil.logic

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.WbUtil
import com.zu.camerautil.util.refreshCameraParams
import com.zu.camerautil.util.setAe
import com.zu.camerautil.util.setFlashMode
import com.zu.camerautil.util.setWb
import com.zu.camerautil.view.CameraLensView
import com.zu.camerautil.view.CameraParamsView
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2024/5/13
 * @description
 */
class CameraControlLogic(val cameraLogic: BaseCameraLogic,
                         val cameraLensView: CameraLensView,
                         val cameraParamsView: CameraParamsView,
                         val configCallback: ConfigCallback) {

    var cameraConfigListener: CameraConfigListener? = null
    var captureCallback: CaptureCallback? = null

    var currentCameraID: String? = null
        private set
    var currentSize: Size? = null
        private set
    var currentFps: FPS? = null
        private set

    private val isWbModeOff: Boolean
        get() {
            val currentWbMode = cameraParamsView.getParamValue(CameraParamID.WB_MODE) as Int
            return currentWbMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
        }

    init {
        init()
    }

    private fun init() {
        initCameraLogic()
        initView()
    }

    private fun initCameraLogic() {
        cameraLogic.configCallback = object : BaseCameraLogic.ConfigCallback {
            override fun getFps(): FPS {
                return cameraLensView.currentFps!!
            }

            override fun getSize(): Size {
                Timber.d("getSize: ${cameraLensView.currentSize}")
                return cameraLensView.currentSize!!
            }

            override fun getTemplate(): Int {
                return configCallback.getTemplate()
            }

            override fun getSessionSurfaceList(): List<Surface> {
                return configCallback.getSessionSurfaceList()
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return configCallback.getCaptureSurfaceList()
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                refreshCameraParams(cameraParamsView, requestBuilder)
                configCallback.configBuilder(requestBuilder)
            }
        }

        cameraLogic.captureCallback = object : CaptureCallback() {
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

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure
            ) {
                captureCallback?.onCaptureFailed(session, request, failure)
            }

            override fun onCaptureSequenceCompleted(
                session: CameraCaptureSession,
                sequenceId: Int,
                frameNumber: Long
            ) {
                captureCallback?.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
            }

            override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
                captureCallback?.onCaptureSequenceAborted(session, sequenceId)
            }

            @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            override fun onReadoutStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                captureCallback?.onReadoutStarted(session, request, timestamp, frameNumber)
            }

            override fun onCaptureBufferLost(
                session: CameraCaptureSession,
                request: CaptureRequest,
                target: Surface,
                frameNumber: Long
            ) {
                captureCallback?.onCaptureBufferLost(session, request, target, frameNumber)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (cameraParamsView.isParamAuto(CameraParamID.SEC)) {
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { sec ->
                        cameraParamsView.post {
                            cameraParamsView.setParamValue(CameraParamID.SEC, sec)
                        }
                    }
                }

                if (cameraParamsView.isParamAuto(CameraParamID.ISO)) {
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { iso ->
                        cameraParamsView.post {
                            cameraParamsView.setParamValue(CameraParamID.ISO, iso)
                        }
                    }
                }

                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let {
                    WbUtil.previousCST = it
                }

                if (!isWbModeOff) {
                    result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
                        val (temp, tint) = WbUtil.computeTempAndTint(it)
                        cameraParamsView.post {
                            cameraParamsView.setParamValue(CameraParamID.TEMP, temp)
                            cameraParamsView.setParamValue(CameraParamID.TINT, tint)
                        }
                    }
                }

//                result.get(CaptureResult.SENSOR_TIMESTAMP)?.let {
//                    Timber.d("timeStamp: ${it / 1000L} ms")
//                }


                captureCallback?.onCaptureCompleted(session, request, result)
            }
        }
    }

    private fun initView() {
        cameraLensView.onConfigChangedListener = { camera, fps, size ->
            var reopenCamera = false
            if (camera.cameraID != currentCameraID) {
                currentCameraID = camera.cameraID
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

            cameraConfigListener?.onCameraConfigChanged(
                camera.cameraID,
                currentSize!!,
                currentFps!!,
                reopenCamera
            )

            if (reopenCamera) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
                // 注意这里回调本来就是在主线程内，但是为什么还要post呢？
                // 因为上面PreviewView设置分辨率后，会调用一次requestLayout或者postInvalidate进行重新布局。
                // 但是重新布局并不会立刻进行而是排进主线程队列里。如果这里直接打开相机，就会导致相机输出时PreviewView
                // 并没有被设置为正确的布局，所以这里把打开相机也post到主线程的队列里并且保证它在重布局PreviewView
                // 的后面
                cameraLensView.post {
                    cameraLogic.closeCamera()
                    cameraLogic.openCamera(camera)
                }
            }
            cameraParamsView.setCameraConfig(camera, size, fps)
        }

        cameraParamsView.addAutoModeListener(CameraParamID.SEC) { secAuto ->
            val isoAuto = cameraParamsView.isParamAuto(CameraParamID.ISO)
            if (secAuto != isoAuto) {
                cameraParamsView.setParamAuto(CameraParamID.ISO, secAuto)
                setSecAndIsoAuto(secAuto)
            }
        }

        cameraParamsView.addValueListener(CameraParamID.SEC) {
            if (!cameraParamsView.isParamAuto(CameraParamID.SEC)) {
                val sec = it as? Long ?: return@addValueListener
                val rational = Rational(1_000_000_000, sec.toInt())
                Timber.d("secChange: $rational")
                cameraLogic.updateCaptureRequestParams { builder ->
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sec)
                }
            }
        }

        cameraParamsView.addAutoModeListener(CameraParamID.ISO) { isoAuto ->
            val secAuto = cameraParamsView.isParamAuto(CameraParamID.SEC)
            if (secAuto != isoAuto) {
                cameraParamsView.setParamAuto(CameraParamID.SEC, isoAuto)
                setSecAndIsoAuto(isoAuto)
            }
        }

        cameraParamsView.addValueListener(CameraParamID.ISO) {
            if (!cameraParamsView.isParamAuto(CameraParamID.ISO)) {
                val iso = it as? Int ?: return@addValueListener
                cameraLogic.updateCaptureRequestParams { builder ->
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                }
            }
        }

        cameraParamsView.addValueListener(CameraParamID.WB_MODE) { mode ->
            val wbMode = mode as? Int ?: return@addValueListener
            val isManual = mode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
            if (!isManual) {
                cameraLogic.updateCaptureRequestParams { builder ->
                    setWb(wbMode, null, null, builder)
                }
            } else {
                val temp = cameraParamsView.getParamValue(CameraParamID.TEMP) as Int
                val tint = cameraParamsView.getParamValue(CameraParamID.TINT) as Int
                cameraLogic.updateCaptureRequestParams { builder ->
                    setWb(wbMode, temp, tint, builder)
                }
            }
            cameraParamsView.setParamEnable(CameraParamID.TEMP, isManual)
            cameraParamsView.setParamEnable(CameraParamID.TINT, isManual)
        }

        cameraParamsView.addValueListener(CameraParamID.TEMP) { value ->
            if (!isWbModeOff) {
                return@addValueListener
            }
            val temp = value as? Int ?: return@addValueListener
            val tint = cameraParamsView.getParamValue(CameraParamID.TINT) as? Int ?: return@addValueListener
            cameraLogic.updateCaptureRequestParams { builder ->
                setWb(CameraCharacteristics.CONTROL_AWB_MODE_OFF, temp, tint, builder)
            }
        }

        cameraParamsView.addValueListener(CameraParamID.TINT) { value ->
            if (!isWbModeOff) {
                return@addValueListener
            }
            val temp = cameraParamsView.getParamValue(CameraParamID.TEMP) as? Int ?: return@addValueListener
            val tint = value as? Int ?: return@addValueListener
            cameraLogic.updateCaptureRequestParams { builder ->
                setWb(CameraCharacteristics.CONTROL_AWB_MODE_OFF, temp, tint, builder)
            }
        }

        cameraParamsView.addValueListener(CameraParamID.FLASH_MODE) { value ->
            val flashMode = value as FlashUtil.FlashMode
            cameraLogic.updateCaptureRequestParams { builder ->
                setFlashMode(flashMode, builder)
            }
        }
    }

    private fun setSecAndIsoAuto(auto: Boolean) {
        if (auto) {
            cameraLogic.updateCaptureRequestParams { builder ->
                setAe(true, null, null, builder)
            }
        } else {
            cameraLogic.updateCaptureRequestParams { builder ->
                val sec = cameraParamsView.getParamValue(CameraParamID.SEC) as Long
                val iso = cameraParamsView.getParamValue(CameraParamID.ISO) as Int
                setAe(false, sec, iso, builder)
            }
        }
    }

    interface ConfigCallback {
        fun getTemplate(): Int
        fun getSessionSurfaceList(): List<Surface>
        fun getCaptureSurfaceList(): List<Surface>
        fun configBuilder(requestBuilder: CaptureRequest.Builder)
    }

    interface CameraConfigListener {
        fun onCameraConfigChanged(cameraID: String, size: Size, fps: FPS, reopen: Boolean)
    }
}