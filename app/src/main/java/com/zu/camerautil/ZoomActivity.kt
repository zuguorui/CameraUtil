package com.zu.camerautil

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityZoomBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber

@SuppressLint("MissingPermission")
class ZoomActivity : AppCompatActivity() {

    // camera objects start

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic

    private var openedCameraID: String? = null
    private var currentSize: Size? = null
    private var currentFps: FPS? = null

    private var currentZoom: Float = 1.0f


    private val surfaceStateListener = object : PreviewViewImplementation.SurfaceStateListener {
        override fun onSurfaceCreated(surface: Surface) {
            Timber.d("surfaceCreated: Thread = ${Thread.currentThread().name}")
            binding.cameraSelector.setCameras(cameraInfoMap.values)
        }

        override fun onSurfaceSizeChanged(surface: Surface, surfaceWidth: Int, surfaceHeight: Int) {
            val surfaceSize = binding.surfaceMain.surfaceSize
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }

    // camera objects end

    private lateinit var binding: ActivityZoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityZoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initCameraLogic()
        initViews()
    }


    override fun onDestroy() {
        cameraLogic.closeDevice()
        super.onDestroy()
    }

    private fun initCameraLogic() {
        cameraLogic = BaseCameraLogic(this)
        cameraLogic.configCallback = object : BaseCameraLogic.ConfigCallback {
            override fun getFps(): FPS {
                if (currentFps != binding.cameraSelector.currentFps) {
                    currentFps = binding.cameraSelector.currentFps
                }
                return currentFps!!
            }

            override fun getSessionSurfaceList(): List<Surface> {
                return arrayListOf(binding.surfaceMain.surface)
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                if (Build.VERSION.SDK_INT >= 30) {
                    requestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom)
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

        cameraLogic.sessionStateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                currentFps = binding.cameraSelector.currentFps
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {

            }
        }
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener


        binding.sb.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                val percent = progress * 1.0f / seekBar.max
                setZoomPercent(percent)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
            if (camera.cameraID != openedCameraID) {
                onCameraChanged()
            }
            if (currentSize !=  binding.cameraSelector.currentSize) {
                currentSize = binding.cameraSelector.currentSize
                binding.surfaceMain.previewSize = currentSize!!
            }
            if (camera.cameraID != openedCameraID || size != currentSize || fps != currentFps) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
                // 注意这里回调本来就是在主线程内，但是为什么还要post呢？
                // 因为上面PreviewView设置分辨率后，会调用一次requestLayout或者postInvalidate进行重新布局。
                // 但是重新布局并不会立刻进行而是排进主线程队列里。如果这里直接打开相机，就会导致相机输出时PreviewView
                // 并没有被设置为正确的布局，所以这里把打开相机也post到主线程的队列里并且保证它在重布局PreviewView
                // 的后面
                binding.root.post {
                    cameraLogic.closeDevice()
                    cameraLogic.openCamera(camera)
                }
            }
        }
    }


    private fun setZoomPercent(percent: Float) {
        if (Build.VERSION.SDK_INT < 30) {
            return
        }
        val info = cameraInfoMap[openedCameraID] ?: return
        val zoomRange = info.zoomRange ?: return

        val zoom = (1 - percent) * zoomRange.lower + percent * zoomRange.upper
        setZoom(zoom)
    }

    private fun setZoom(zoom: Float) {
        computeZoom(zoom)
        binding.tvCurrent.text = String.format("%.2f", currentZoom)
        cameraLogic.updateCaptureRequestParams()
    }

    private fun computeZoom(zoom: Float) {
        if (Build.VERSION.SDK_INT < 30) {
            return
        }
        val info = binding.cameraSelector.currentCamera
        val zoomRange = info.zoomRange ?: return

        val min = zoomRange.lower
        val max = zoomRange.upper

        val finalZoom = if (zoom < min) {
            min
        } else if (zoom > max) {
            max
        } else {
            zoom
        }
        currentZoom = finalZoom
    }

    private fun onCameraChanged() {
        val info = binding.cameraSelector.currentCamera

        val zoomRange = info.zoomRange

        val supportZoom = (Build.VERSION.SDK_INT >= 30 && zoomRange != null)
        binding.sb.isEnabled = supportZoom
        binding.sb.progress = computeProgress(1.0f)
        computeZoom(1.0f)
        binding.tvCurrent.text = if (supportZoom) "1.0" else "不支持"
        binding.tvMin.text = if (supportZoom) String.format("%.2f", zoomRange!!.lower) else "Null"
        binding.tvMax.text = if (supportZoom) String.format("%.2f", zoomRange!!.upper) else "Null"
    }

    private fun computeProgress(zoom: Float): Int {
        val info = cameraInfoMap[openedCameraID] ?: kotlin.run {
            return 0
        }
        val zoomRange = info.zoomRange ?: return 0

        val percent = (zoom - zoomRange.lower) * 1.0f / (zoomRange.upper - zoomRange.lower).apply {
            if (this < 0) {
                0
            } else if (this > 1) {
                1
            } else {
                this
            }
        }

        val max = binding.sb.max
        val min = if (Build.VERSION.SDK_INT >= 26) binding.sb.min else 0

        return ((max - min) * percent + min).toInt()
    }

}