package com.zu.camerautil

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceProfiles
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraUsage
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.CTS_COLOR
import com.zu.camerautil.camera.computeRggbChannelVector
import com.zu.camerautil.camera.getWbModeName
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityWbBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber

@SuppressLint("MissingPermission")
class WbActivity : AppCompatActivity() {

    private val TEMP_MAX = 1
    private val TEMP_MIN = 0
    private val TINT_MAX = 1
    private val TINT_MIN = -1

    private val gain = 2.0f
    private val testVector = RggbChannelVector(1.0f * gain, 0.5f * gain, 0.5f * gain, 1.0f * gain)

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
            binding.cameraSelector.setCameras(cameraInfoMap.values)
        }

        override fun onSurfaceSizeChanged(surface: Surface, surfaceWidth: Int, surfaceHeight: Int) {
            val surfaceSize = Size(surfaceWidth, surfaceHeight)
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }

    private lateinit var binding: ActivityWbBinding
    private lateinit var wbModeAdapter: ArrayAdapter<String>

    private lateinit var rggbChannelVector: RggbChannelVector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWbBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initCameraLogic()
        initViews()
        updateRggbChannelVector()
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

            override fun getUsage(): CameraUsage {
                return CameraUsage.PREVIEW
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                var wbMode = CameraCharacteristics.CONTROL_AWB_MODE_OFF
                if (binding.spWbMode.selectedItemPosition >= 0) {
                    cameraLogic.currentCameraInfo?.awbModes?.get(binding.spWbMode.selectedItemPosition)?.let {
                        wbMode = it
                    }
                }
                requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, wbMode)
                if (wbMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF) {
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, CTS_COLOR)
                    //requestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, testVector)

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
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false
            if (camera.cameraID != openedCameraID) {
                updateWbModes(camera)
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

        wbModeAdapter = ArrayAdapter(this, R.layout.item_camera_simple,R.id.tv)
        binding.spWbMode.adapter = wbModeAdapter
        binding.spWbMode.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val mode = cameraLogic.currentCameraInfo?.awbModes?.get(position) ?: return
                val isManual = mode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
                binding.tvInputTemp.visibility = if (isManual) View.VISIBLE else View.VISIBLE
                binding.sbTemp.isEnabled = isManual
                binding.sbTint.isEnabled = isManual
                cameraLogic.updateCaptureRequestParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }

        binding.tvMaxTemp.text = "$TEMP_MAX"
        binding.tvMinTemp.text = "$TEMP_MIN"
        binding.tvMaxTint.text = "$TINT_MAX"
        binding.tvMinTint.text = "$TINT_MIN"

        binding.sbTemp.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateRggbChannelVector()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        binding.sbTint.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateRggbChannelVector()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

    }

    private fun updateRggbChannelVector() {
        val tempProgress = binding.sbTemp.progress.toFloat() / binding.sbTemp.max
        val tintProgress = binding.sbTint.progress.toFloat() / binding.sbTint.max
        val temp = (1 - tempProgress) * TEMP_MIN + tempProgress * TEMP_MAX
        val tint = (1 - tintProgress) * TINT_MIN + tintProgress * TINT_MAX
        binding.tvInputTemp.text = "色温(输入): ${String.format("%.2f", temp)}"
        binding.tvTint.text = "色调: ${String.format("%.2f", tint)}"

        rggbChannelVector = computeRggbChannelVector(temp, tint)
        cameraLogic.updateCaptureRequestParams()
    }

    private fun updateWbModes(cameraInfo: CameraInfoWrapper) {
        val nameList = ArrayList<String>()
        cameraInfo.awbModes?.forEach {
            nameList.add(getWbModeName(it) ?: "$it")
        }
        wbModeAdapter.clear()
        wbModeAdapter.addAll(nameList)
    }
}