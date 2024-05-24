package com.zu.camerautil

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
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
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.WbUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityWbBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class WbActivity : AppCompatActivity() {

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

    private val currentMode: Int
        get() {
            return openedCameraID?.let { id ->
                val cameraInfo = cameraInfoMap[id]
                val index = binding.spWbMode.selectedItemPosition
                cameraInfo!!.awbModes!![index]
            } ?: CameraCharacteristics.CONTROL_AWB_MODE_AUTO
        }


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

            override fun getTemplate(): Int {
                return CameraDevice.TEMPLATE_PREVIEW
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
//                if (currentMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF) {
//                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
//                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
//                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
//                }
            }
        }

        cameraLogic.cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Timber.w("onOpened: camera${camera.id}")
                openedCameraID = camera.id
            }

            override fun onClosed(camera: CameraDevice) {
                Timber.w("onClosed: camera${camera.id}")
                openedCameraID = null
                WbUtil.previousCST = null
            }

            override fun onDisconnected(camera: CameraDevice) {
                Timber.w("onDisconnected: camera${camera.id}")
                openedCameraID = null
                WbUtil.previousCST = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Timber.e("onError: camera${camera.id}, error = $error")
                openedCameraID = null
                WbUtil.previousCST = null
            }
        }

        cameraLogic.captureCallback = object : CameraCaptureSession.CaptureCallback() {

            var debugGain: RggbChannelVector? = null
            var debugTransform: ColorSpaceTransform? = null
            val formatText = "%.3f"

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                val currentMode = currentMode
                var newSetCST = false
                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let {
                    if (debugTransform == null || it != debugTransform) {
                        debugTransform = it
                        val sb = StringBuilder("transform:\n")
                        for (row in 0 until 3) {
                            for (col in 0 until 3) {
                                sb.append(String.format("%.2f", it.getElement(col, row).toFloat()))
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
                    if (WbUtil.previousCST == null) {
                        newSetCST = true
                    }
                    WbUtil.previousCST = it
                }

                result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
                    if (debugGain == null || !isColorGainEqual(debugGain!!, it)) {
                        debugGain = it
//                        Timber.d("ColorGain: [red = ${String.format(formatText, it.red)}, greenEven = ${String.format(formatText, it.greenEven)}, greenOdd = ${String.format(formatText, it.greenOdd)}, blue = ${String.format(formatText, it.blue)}]")
//                        Timber.d("ColorGain baseLine: ${String.format(formatText, (it.red + it.blue) / 2)}")
                    }
                    val isAwbOff = result.get(CaptureResult.CONTROL_AWB_MODE) == CaptureResult.CONTROL_AWB_MODE_OFF
                    val (temp, tint) = WbUtil.computeTempAndTint(it)
                    runOnUiThread {
                        binding.tvTempAnalyze.text = "$temp"
                        binding.tvTintAnalyze.text = "$tint"
                    }


                    if (currentMode != CameraCharacteristics.CONTROL_AWB_MODE_OFF) {
                        runOnUiThread {
                            binding.sbTemp.run {
                                val p = (temp.toFloat() - WbUtil.TEMP_RANGE.lower) / (WbUtil.TEMP_RANGE.upper - WbUtil.TEMP_RANGE.lower)
                                progress = (p * max).roundToInt()
                            }

                            binding.sbTint.run {
                                val p = (tint.toFloat() - WbUtil.TINT_RANGE.lower) / (WbUtil.TINT_RANGE.upper - WbUtil.TINT_RANGE.lower)
                                progress = (p * max).roundToInt()
                            }
                        }

                    }
                }

                if (newSetCST && currentMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF && WbUtil.previousCST != null) {
                    Timber.w("mode is off, and newly set CST, update wb to off")
                    updateRggbChannelVector()
                    cameraLogic.updateCaptureRequestParams {
                        Timber.d("update to wb mode")
                        it.apply {
                            set(CaptureRequest.CONTROL_AWB_MODE, CameraCharacteristics.CONTROL_AWB_MODE_OFF)
                            set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                            set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
                            set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
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
        binding.spWbMode.onItemSelectedListener = object : OnItemSelectedListener {
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
                cameraLogic.updateCaptureRequestParams {
                    it.set(CaptureRequest.CONTROL_AWB_MODE, mode)
                    if (isManual) {
                        it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                        it.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
                        it.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
                    } else {
                        it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }

        binding.tvMaxTemp.text = "${WbUtil.TEMP_RANGE.upper}"
        binding.tvMinTemp.text = "${WbUtil.TEMP_RANGE.lower}"
        binding.tvMaxTint.text = "${WbUtil.TINT_RANGE.upper}"
        binding.tvMinTint.text = "${WbUtil.TINT_RANGE.lower}"

        binding.sbTemp.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRggbChannelVector()
                //Timber.d("temp changed, from user = $fromUser")
                if (fromUser) {
                    cameraLogic.updateCaptureRequestParams {
                        it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                        it.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
                        it.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        binding.sbTint.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRggbChannelVector()
                //Timber.d("tint changed, from user = $fromUser")
                if (fromUser) {
                    cameraLogic.updateCaptureRequestParams {
                        it.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                        it.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
                        it.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
                    }
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
        val temp = ((1 - tempProgress) * WbUtil.TEMP_RANGE.lower + tempProgress * WbUtil.TEMP_RANGE.upper).roundToInt()
        val tint = ((1 - tintProgress) * WbUtil.TINT_RANGE.lower + tintProgress * WbUtil.TINT_RANGE.upper).roundToInt()
        binding.tvTempInput.text = "$temp"
        binding.tvTintInput.text = "$tint"
        //Timber.d("updateRggbChannelVector: temp = $temp, tint = $tint")
        rggbChannelVector = WbUtil.computeRggbChannelVector(temp, tint)
    }

    private fun updateWbModes(cameraInfo: CameraInfoWrapper) {
        val currentMode = currentMode
        val nameList = ArrayList<String>()

        cameraInfo.awbModes!!.forEach {
            nameList.add(WbUtil.getWbModeName(it) ?: "$it")
        }
        wbModeAdapter.clear()
        wbModeAdapter.addAll(nameList)

        val initMode = if (cameraInfo.awbModes!!.contains(currentMode)) {
            currentMode
        } else {
            CameraCharacteristics.CONTROL_AWB_MODE_AUTO
        }
//        val initMode = CameraCharacteristics.CONTROL_AWB_MODE_AUTO
        val index = cameraInfo.awbModes!!.indexOf(initMode)
        binding.spWbMode.setSelection(index)
        binding.sbTemp.isEnabled = (initMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF)
        binding.sbTint.isEnabled = (initMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF)
    }
}