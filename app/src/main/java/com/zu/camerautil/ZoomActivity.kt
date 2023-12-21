package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
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
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.computePreviewSize
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityZoomBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.view.CameraSpinnerAdapter
import timber.log.Timber
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class ZoomActivity : AppCompatActivity() {

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

    private lateinit var binding: ActivityZoomBinding
    private lateinit var adapter: CameraSpinnerAdapter
    private val cameraList: ArrayList<CameraInfoWrapper> by lazy {
        ArrayList<CameraInfoWrapper>().apply {
            addAll(cameraInfoMap.values)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityZoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        openedCameraID = null
        surfaceCreated = false
        initViews()
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
    }

    override fun onDestroy() {
        closeDevice()
        super.onDestroy()
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        adapter = CameraSpinnerAdapter()
        adapter.setData(cameraList)
        binding.spinnerCamera.adapter = adapter
        binding.spinnerCamera.onItemSelectedListener = object : OnItemSelectedListener {
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
                this@ZoomActivity.camera = camera
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
                this@ZoomActivity.session = session
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
        if (Build.VERSION.SDK_INT < 30) {
            return
        }
        val builder = captureRequestBuilder ?: return
        val info = cameraInfoMap[openedCameraID] ?: return
        val session = this@ZoomActivity.session ?: return
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
        binding.tvCurrent.text = String.format("%.2f", finalZoom)
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, finalZoom)
        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    private fun onCameraChanged() {
        val info = cameraInfoMap[openedCameraID] ?: kotlin.run {
            binding.sb.isEnabled = false
            return
        }

        val zoomRange = info.zoomRange

        val supportZoom = (Build.VERSION.SDK_INT >= 30 && zoomRange != null)
        binding.sb.isEnabled = supportZoom
        binding.sb.progress = computeProgress(1.0f)
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