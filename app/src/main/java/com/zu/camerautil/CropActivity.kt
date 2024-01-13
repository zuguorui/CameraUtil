package com.zu.camerautil

import android.annotation.SuppressLint
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.widget.SeekBar
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraUsage
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityCropBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber

@SuppressLint("MissingPermission")
class CropActivity : AppCompatActivity() {

    // camera objects start


    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic

    private var openedCameraID: String? = null
    private var currentSize: Size? = null
    private var currentFps: FPS? = null

    private var currentCrop: Float = 1.0f
    private var currentCropRect = Rect()


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

    private lateinit var binding: ActivityCropBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
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

            override fun getSize(): Size {
                return currentSize!!
            }

            override fun getUsage(): CameraUsage {
                return CameraUsage.PREVIEW
            }

            override fun getSessionSurfaceList(): List<Surface> {
                return arrayListOf(binding.surfaceMain.surface)
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, currentCropRect)
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
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                val percent = progress * 1.0f / seekBar.max
                setCropPercent(percent)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false
            if (camera.cameraID != openedCameraID) {
                onCameraChanged()
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
        }
    }


    private fun setCropPercent(percent: Float) {
        val info = cameraInfoMap[openedCameraID] ?: return

        val min = 1.0f
        val max = info.maxDigitalZoom

        val crop = (1 - percent) * min + percent * max

        setCrop(crop)
    }



    private fun setCrop(crop: Float) {

        computeCrop(crop)

        binding.tvCurrent.text = String.format("%.2f", currentCrop)

        cameraLogic.updateCaptureRequestParams()
    }

    private fun computeCrop(crop: Float) {
        val info = binding.cameraSelector.currentCamera

        val sensorSize = info.activeArraySize
        val centerX = sensorSize.centerX()
        val centerY = sensorSize.centerY()

        val min = 1.0f
        val max = info.maxDigitalZoom

        val finalCrop = if (crop < min) {
            min
        } else if (crop > max) {
            max
        } else {
            crop
        }

        val width = (sensorSize.width() / finalCrop).toInt()
        val height = (sensorSize.height() / finalCrop).toInt()
        val left = centerX - width / 2
        val right = centerX + width / 2
        val top = centerY - height / 2
        val bottom = centerY + height / 2
        val rect = Rect(left, top, right, bottom)

        currentCrop = finalCrop
        currentCropRect = rect
    }

    private fun onCameraChanged() {
        val info = binding.cameraSelector.currentCamera
        binding.sb.progress = computeProgress(1.0f)
        computeCrop(1.0f)
        binding.tvCurrent.text = String.format("%.2f", currentCrop)
        binding.tvMin.text = String.format("%.2f", 1.0f)
        binding.tvMax.text = String.format("%.2f", info.maxDigitalZoom)
    }

    private fun computeProgress(crop: Float): Int {
        val info = cameraInfoMap[openedCameraID] ?: kotlin.run {
            return 0
        }

        val percent = (crop - 1.0f) / (info.maxDigitalZoom - 1.0f).apply {
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