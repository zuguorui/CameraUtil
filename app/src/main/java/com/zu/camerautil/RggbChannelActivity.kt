package com.zu.camerautil

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.Bundle
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.WbUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityRggbChannelBinding
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

    private var red: Float
        get() = binding.sliderRed.value
        set(value) {
            binding.sliderRed.value = value
        }

    private var greenEven: Float
        get() = binding.sliderGreenEven.value
        set(value) {
            binding.sliderGreenEven.value = value
        }

    private var greenOdd: Float
        get() = binding.sliderGreenOdd.value
        set(value) {
            binding.sliderGreenOdd.value = value
        }

    private var blue: Float
        get() = binding.sliderBlue.value
        set(value) {
            binding.sliderBlue.value = value
        }

    private var bindGreen: Boolean
        get() = binding.swBindGreen.isChecked
        set(value) {
            binding.swBindGreen.isChecked = value
        }

    private var isAuto: Boolean
        get() = binding.swAuto.isChecked
        set(value) {
            binding.swAuto.isChecked = value
        }

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

            override fun getTemplate(): Int {
                return CameraDevice.TEMPLATE_PREVIEW
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                if (!isAuto) {
                    cameraLogic.updateCaptureRequestParams {
                        it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                        it.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
                        it.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                        val rggbChannelVector = RggbChannelVector(red, greenEven, greenOdd, blue)
                        it.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
                    }
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

                result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
                    if (debugGain == null || !isColorGainEqual(debugGain!!, it)) {
                        debugGain = it
                        Timber.d("ColorGain: [red = ${String.format(formatText, it.red)}, greenEven = ${String.format(formatText, it.greenEven)}, greenOdd = ${String.format(formatText, it.greenOdd)}, blue = ${String.format(formatText, it.blue)}]")
                        Timber.d("ColorGain baseLine: ${String.format(formatText, (it.red + it.blue) / 2)}")
                    }
                    if (isAuto) {
                        runOnUiThread {
                            red = it.red
                            greenEven = it.greenEven
                            greenOdd = it.greenOdd
                            blue = it.blue
                        }
                    }
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

        binding.sliderRed.apply {
            value = (MIN + MAX) / 2
            valueFrom = MIN
            valueTo = MAX
            addOnChangeListener { _, value, fromUser ->
                binding.tvValueRed.text = String.format("%.2f", value)
                if (fromUser) {
                    updateRggbChannel(value, greenEven, greenOdd, blue)
                }
            }
        }


        binding.sliderGreenEven.apply {
            value = (MIN + MAX) / 2
            valueFrom = MIN
            valueTo = MAX
            addOnChangeListener { _, value, fromUser ->
                binding.tvValueGreenEven.text = String.format("%.2f", value)
                if (fromUser) {
                    if (bindGreen) {
                        greenOdd = value
                    }
                    updateRggbChannel(red, value, greenOdd, blue)
                }
            }
        }

        binding.sliderGreenOdd.apply {
            value = (MIN + MAX) / 2
            valueFrom = MIN
            valueTo = MAX
            addOnChangeListener { _, value, fromUser ->
                binding.tvValueGreenOdd.text = String.format("%.2f", value)
                if (fromUser) {
                    if (bindGreen) {
                        greenEven = value
                    }
                    updateRggbChannel(red, greenEven, value, blue)
                }
            }
        }

        binding.sliderBlue.apply {
            value = (MIN + MAX) / 2
            valueFrom = MIN
            valueTo = MAX
            addOnChangeListener { _, value, fromUser ->
                binding.tvValueBlue.text = String.format("%.2f", value)
                if (fromUser) {
                    updateRggbChannel(red, greenEven, greenOdd, value)
                }
            }
        }

        binding.swBindGreen.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAuto) {
                greenOdd = greenEven
            }
        }

        binding.swAuto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                cameraLogic.updateCaptureRequestParams {
                    it.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
                }
            } else {
                if (bindGreen) {
                    greenOdd = greenEven
                }
                cameraLogic.updateCaptureRequestParams {
                    it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    it.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
                    it.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                    val rggbChannelVector = RggbChannelVector(red, greenEven, greenOdd, blue)
                    it.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
                }
            }

            binding.sliderRed.isEnabled = !isChecked
            binding.sliderGreenEven.isEnabled = !isChecked
            binding.sliderGreenOdd.isEnabled = !isChecked
            binding.sliderBlue.isEnabled = !isChecked
        }

        binding.sliderRed.isEnabled = !isAuto
        binding.sliderGreenEven.isEnabled = !isAuto
        binding.sliderGreenOdd.isEnabled = !isAuto
        binding.sliderBlue.isEnabled = !isAuto

    }

    private fun updateRggbChannel(red: Float, greenEven: Float, greenOdd: Float, blue: Float) {
        cameraLogic.updateCaptureRequestParams {
            val rggbChannelVector = RggbChannelVector(red, greenEven, greenOdd, blue)
            it.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
        }
    }

    companion object {
        private val MAX = 4.0f
        private val MIN = 0.0f
    }
}