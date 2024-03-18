package com.zu.camerautil

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.Bundle
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.CameraUsage
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.WbUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityRggbChannelBinding
import com.zu.camerautil.databinding.ActivitySecAndIsoBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber

class RggbChannelActivity : AppCompatActivity() {

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
            binding.cameraLensView.setCameras(cameraInfoMap.values)
        }

        override fun onSurfaceSizeChanged(surface: Surface, surfaceWidth: Int, surfaceHeight: Int) {
            val surfaceSize = Size(surfaceWidth, surfaceHeight)
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }

    private lateinit var binding: ActivityRggbChannelBinding

    // 镜头是否刚刚打开
    private var justOpenCamera = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRggbChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
                return arrayListOf(binding.preview.surface)
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
            }

            override fun getSize(): Size {
                return currentSize!!
            }

            override fun getUsage(): CameraUsage {
                return CameraUsage.PREVIEW
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
                var debugGain: RggbChannelVector? = null
                var debugTransform: ColorSpaceTransform? = null
                val formatText = "%.3f"

                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let {
                    if (debugTransform == null || it != debugTransform) {
                        debugTransform = it
                        val sb = StringBuilder("transform:\n")
                        for (row in 0 until 3) {
                            for (col in 0 until 3) {
                                sb.append("${it.getElement(col, row)}")
                                if (col < 2) {
                                    sb.append(", ")
                                }
                            }
                            if (row < 2) {
                                sb.append("\n")
                            }
                        }
                        Timber.d(sb.toString())
                    }

                    WbUtil.previousCST = it
                }
            }
        }
    }

    private fun initViews() {
        binding.preview.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.preview.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.preview.surfaceStateListener = surfaceStateListener

        binding.cameraLensView.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false
            if (camera.cameraID != openedCameraID) {
                reopenCamera = true
            }

            if (size != currentSize) {
                currentSize = size
                binding.preview.previewSize = currentSize!!
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
        }
    }
}