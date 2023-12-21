package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.computePreviewSize
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityCropBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.view.CameraAdapter
import timber.log.Timber
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class CropActivity : AppCompatActivity() {

    // camera objects start
    private val cameraManager: CameraManager by lazy {
        ((getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private var surfaceCreated = false
    private var openedCameraID: String? = null

    private var camera: CameraDevice? = null

    private var session: CameraCaptureSession? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private lateinit var previewSize: Size

    private var cameraThread = HandlerThread("CameraThread").apply { start() }

    private var cameraHandler = Handler(cameraThread.looper)

    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

    }
    // camera objects end

    private val surfaceStateListener = object : PreviewViewImplementation.SurfaceStateListener {
        override fun onSurfaceCreated(surface: Surface) {
            Timber.d("surfaceCreated: Thread = ${Thread.currentThread().name}")
            surfaceCreated = true
            val cameraID = selectCameraID(cameraInfoMap, CameraCharacteristics.LENS_FACING_BACK, true)
            binding.spinnerCamera.setSelection(cameraList.indexOfFirst { info -> info.cameraID == cameraID })
        }

        override fun onSurfaceSizeChanged(surface: Surface, width: Int, height: Int) {
            val surfaceSize = binding.surfaceMain.surfaceSize
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }

    private lateinit var binding: ActivityCropBinding
    private lateinit var adapter: CameraAdapter
    private val cameraList: ArrayList<CameraInfoWrapper> by lazy {
        ArrayList<CameraInfoWrapper>().apply {
            addAll(cameraInfoMap.values)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)
        openedCameraID = null
        surfaceCreated = false
        initViews()
    }

    override fun onDestroy() {
        closeDevice()
        super.onDestroy()
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        adapter = CameraAdapter()
        adapter.setData(cameraList)
        binding.spinnerCamera.adapter = adapter
        binding.spinnerCamera.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val cameraID = cameraList[position]!!.cameraID
                openDevice(cameraID!!)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                closeDevice()
            }
        }


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
    }

    @Synchronized
    private fun openDevice(cameraID: String) {
        if (openedCameraID == cameraID) {
            Timber.w("same cameraID, return")
        }
        if (camera != null) {
            closeDevice()
        }
        openedCameraID = cameraID
        val characteristics = cameraInfoMap[cameraID]!!.characteristics
        computeSize(characteristics)
        // 获取最接近容器宽高比的Camera输出，并且设置给SurfaceView
        binding.surfaceMain.previewSize = previewSize

        val info = cameraInfoMap[openedCameraID]!!
        val finalID = info.logicalID ?: openedCameraID!!
        Timber.d("openDevice $finalID")
        cameraManager.openCamera(finalID, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@CropActivity.camera = camera
                createSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Timber.e("open camera failed")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraID error: ($error) $msg")
                Timber.e(exc.message, exc)
            }
        }, cameraHandler)
    }

    private fun closeDevice() {
        closeSession()
        camera?.close()
        camera = null
        openedCameraID = null
    }

    private fun createSession() {
        val camera = this.camera ?: return

        val createSessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                this@CropActivity.session = session
                startPreview()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exception = RuntimeException("create session failed")
                Timber.e("onConfigureFailed: ${exception.message}")
            }
        }

        val target = getSessionSurfaceList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = cameraInfoMap[openedCameraID]!!
            val outputConfigurations = ArrayList<OutputConfiguration>()
            for (surface in target) {
                val outputConfiguration = OutputConfiguration(surface)
                if (info.logicalID != null) {
                    Timber.w("camera${info.cameraID} belong to logical camera${info.logicalID}, set physical camera")
                    outputConfiguration.setPhysicalCameraId(info.cameraID)
                }
                outputConfigurations.add(outputConfiguration)
            }
            camera.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, cameraExecutor, createSessionCallback))
        } else {
            camera.createCaptureSession(target, createSessionCallback, cameraHandler)
        }
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun startPreview() {
        val session = this.session ?: return
        val camera = this.camera ?: return

        captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

            getCaptureSurfaceList().forEach {
                addTarget(it)
            }
        }

        session.setRepeatingRequest(captureRequestBuilder!!.build(), captureCallback, cameraHandler)
        runOnUiThread {
            onCameraChanged()
        }

    }

    private fun getSessionSurfaceList(): ArrayList<Surface> {
        return arrayListOf(binding.surfaceMain.surface)
    }

    private fun getCaptureSurfaceList(): ArrayList<Surface> {
        return arrayListOf(binding.surfaceMain.surface)
    }

    private fun computeSize(characteristics: CameraCharacteristics) {
        val viewSize = with(binding.surfaceMain) {
            Size(width, height)
        }
        previewSize = computePreviewSize(
            characteristics,
            viewSize,
            binding.root.display.rotation,
            SurfaceHolder::class.java
        )

        //previewSize = Size(1280, 720)
    }


    private fun setCropPercent(percent: Float) {
        val info = cameraInfoMap[openedCameraID] ?: return

        val min = 1.0f
        val max = info.maxDigitalZoom

        val crop = (1 - percent) * min + percent * max

        setCrop(crop)
    }

    private fun setCrop(crop: Float) {
        val builder = captureRequestBuilder ?: return
        val info = cameraInfoMap[openedCameraID] ?: return
        val session = this@CropActivity.session ?: return


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

        binding.tvCurrent.text = String.format("%.2f", finalCrop)
        builder.set(CaptureRequest.SCALER_CROP_REGION, rect)
        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    private fun onCameraChanged() {
        val info = cameraInfoMap[openedCameraID] ?: kotlin.run {
            binding.sb.isEnabled = false
            return
        }


        binding.sb.progress = computeProgress(1.0f)
        binding.tvCurrent.text = "1.0"
        binding.tvMin.text = String.format("%.2f", 1.0f)
        binding.tvMax.text = String.format("%.2f", info.maxDigitalZoom)
    }

    private fun computeProgress(crop: Float): Int {
        val info = cameraInfoMap[openedCameraID] ?: kotlin.run {
            return 0
        }

        val zoomRange = info.zoomRange ?: return 0

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