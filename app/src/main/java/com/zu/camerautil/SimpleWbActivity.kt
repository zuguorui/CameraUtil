package com.zu.camerautil

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.RggbChannelVector
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraUsage
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.WbUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivitySimpleWbBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber
import kotlin.math.roundToInt

class SimpleWbActivity : AppCompatActivity() {
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

    private lateinit var binding: ActivitySimpleWbBinding
    private lateinit var wbModeAdapter: ArrayAdapter<String>

    private lateinit var rggbChannelVector: RggbChannelVector

    private val currentMode: Int
        get() {
            return openedCameraID?.let { id ->
                val cameraInfo = cameraInfoMap[id]
                val index = binding.spWbMode.selectedItemPosition
                cameraInfo!!.awbModes!![index]
            } ?: CameraCharacteristics.CONTROL_AWB_MODE_OFF
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleWbBinding.inflate(layoutInflater)
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
                val currentMode = currentMode

                requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, currentMode)
                if (currentMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF) {
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.phoneColorSpaceTransform ?: WbUtil.CTS_COLOR)
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
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

                Timber.d("onCaptureComplete, thread = ${Thread.currentThread().name}")

                val currentMode = currentMode

                if (WbUtil.phoneColorSpaceTransform == null) {

                    val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
                    WbUtil.phoneColorSpaceTransform = transform
                    var sb = StringBuilder()
                    for (row in 0..2) {
                        for (col in 0..2) {
                            sb.append(String.format("%.2f", transform!!.getElement(col, row).toFloat()))
                            if (col < 2) {
                                sb.append(", ")
                            }
                        }
                        if (row < 2) {
                            sb.append("\n")
                        }
                    }
                    Timber.d("phone color transform:\n$sb")
                }
                result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
                    val temp = WbUtil.computeTemp(it)

                    runOnUiThread {
                        binding.tvTempAnalyze.text = "$temp"
                    }


                    if (currentMode != CameraCharacteristics.CONTROL_AWB_MODE_OFF) {
                        runOnUiThread {
                            binding.sbTemp.run {
                                val p = (temp.toFloat() - WbUtil.TEMP_RANGE.lower) / (WbUtil.TEMP_RANGE.upper - WbUtil.TEMP_RANGE.lower)
                                progress = (p * max).roundToInt()
                            }
                        }

                    }
                }

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
        binding.spWbMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val mode = cameraLogic.currentCameraInfo?.awbModes?.get(position) ?: return
                val isManual = mode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
                binding.sbTemp.isEnabled = isManual
                binding.sbTint.isEnabled = isManual
                cameraLogic.updateCaptureRequestParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }

        binding.tvMaxTemp.text = "${WbUtil.TEMP_RANGE.upper}"
        binding.tvMinTemp.text = "${WbUtil.TEMP_RANGE.lower}"
        binding.tvMaxTint.text = "${WbUtil.TINT_RANGE.upper}"
        binding.tvMinTint.text = "${WbUtil.TINT_RANGE.lower}"

        binding.sbTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRggbChannelVector()
                Timber.d("temp changed, from user = $fromUser")
                if (fromUser) {
                    cameraLogic.updateCaptureRequestParams()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        binding.sbTint.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRggbChannelVector()
                Timber.d("tint changed, from user = $fromUser")
                if (fromUser) {
                    cameraLogic.updateCaptureRequestParams()
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
        //val tintProgress = binding.sbTint.progress.toFloat() / binding.sbTint.max
        val temp = ((1 - tempProgress) * WbUtil.TEMP_RANGE.lower + tempProgress * WbUtil.TEMP_RANGE.upper).roundToInt()
        //val tint = ((1 - tintProgress) * WbUtil.TINT_RANGE.lower + tintProgress * WbUtil.TINT_RANGE.upper).roundToInt()
        binding.tvTempInput.text = "$temp"
        //binding.tvTintInput.text = "$tint"
        rggbChannelVector = WbUtil.computeRggbChannelVector(temp)
    }

    private fun updateWbModes(cameraInfo: CameraInfoWrapper) {
        val nameList = ArrayList<String>()

        cameraInfo.awbModes!!.forEach {
            nameList.add(WbUtil.getWbModeName(it) ?: "$it")
        }
        wbModeAdapter.clear()
        wbModeAdapter.addAll(nameList)
        val index = cameraInfo.awbModes!!.indexOf(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
        binding.spWbMode.setSelection(index)
        binding.sbTemp.isEnabled = false
        binding.sbTint.isEnabled = false
    }
}