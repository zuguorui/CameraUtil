package com.zu.camerautil.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import com.zu.camerautil.OpenCameraMethod
import com.zu.camerautil.Settings
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * @author zuguorui
 * @date 2024/1/11
 * @description 使用协程将回调改为同步调用。由于openCamera和createSession都是异步回调结果，
 * 导致部分情况下会出现多线程问题
 * 官方协程文档：https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md
 */
@SuppressLint("MissingPermission")
class CoroutineCameraLogic(context: Context): BaseCameraLogic(context) {

    private var cameraScope = CoroutineScope(Job() + Dispatchers.IO)

    private var lock = ReentrantLock(true)

    override fun openCamera(cameraInfo: CameraInfoWrapper) {
        cameraScope.launch {
            if (!openCameraInner(cameraInfo)) {
                Timber.e("openDevice failed")
                lock.unlock()
                return@launch
            }
            createSession()
        }
    }

    override fun createSession() {
        cameraScope.launch {
            if (!createSessionInner()) {
                Timber.e("createSession failed")
                return@launch
            }
            startPreview()
        }
    }


    private suspend fun openCameraInner(cameraInfo: CameraInfoWrapper): Boolean {
        val configCallback = configCallback ?: return false
        if (camera != null) {
            closeDevice()
        }
        val finalID = if (cameraInfo.isInCameraIdList) {
            cameraInfo.cameraID
        } else {
            if (Settings.openCameraMethod == OpenCameraMethod.IN_CONFIGURATION) {
                cameraInfo.logicalID ?: cameraInfo.cameraID
            } else {
                cameraInfo.cameraID
            }
        }
        Timber.d("openDevice $finalID")

        return suspendCoroutine {
            cameraManager.openCamera(finalID, object : CameraDevice.StateCallback() {
                override fun onOpened(pCamera: CameraDevice) {
                    cameraStateCallback?.onOpened(pCamera)
                    currentCameraInfo = cameraInfo
                    camera = pCamera
                    it.resume(true)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraStateCallback?.onDisconnected(camera)
                    Timber.e("camera ${camera.id} disconnected")
                    camera.close()
                    // it.resume(false)
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
                    it.resume(false)
                }
            }, cameraHandler)
        }
    }

    private suspend fun createSessionInner(): Boolean {
        return suspendCoroutine {
            val camera = this.camera ?: kotlin.run {
                it.resume(false)
                return@suspendCoroutine
            }
            val configCallback = configCallback ?: kotlin.run {
                it.resume(false)
                return@suspendCoroutine
            }
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
                    it.resume(true)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    sessionStateCallback?.onConfigureFailed(session)
                    val exception = RuntimeException("create session failed")
                    Timber.e("onConfigureFailed: ${exception.message}, session: $session")
                    it.resume(false)
                }
            }

            val isHighSpeed = currentFps!!.type == FPS.Type.HIGH_SPEED

            val target = configCallback.getSessionSurfaceList()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = currentCameraInfo!!
                val outputConfigurations = ArrayList<OutputConfiguration>()
                for (surface in target) {
                    val outputConfiguration = OutputConfiguration(surface)
                    if (!isHighSpeed && info.logicalID != null && !info.isInCameraIdList && Settings.openCameraMethod == OpenCameraMethod.IN_CONFIGURATION) {
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
    }


}