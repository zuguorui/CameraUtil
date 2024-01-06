package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.ContentValues
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
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.computePreviewSize
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityRecordBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.recorder.IRecorder
import com.zu.camerautil.recorder.RecorderParams
import com.zu.camerautil.recorder.SystemRecorder
import com.zu.camerautil.view.CameraSpinnerAdapter
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class RecordActivity : AppCompatActivity() {

    // camera objects start
    private val cameraManager: CameraManager by lazy {
        ((getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private val cameraList: ArrayList<CameraInfoWrapper> by lazy {
        ArrayList<CameraInfoWrapper>().apply {
            addAll(cameraInfoMap.values)
        }
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

    private lateinit var adapter: CameraSpinnerAdapter
    // camera objects end

    private var recorder: IRecorder = SystemRecorder()
    private var recording = false
        set(value) {
            field = value
            binding.btnRecord.text = if (value) "停止录制" else "开始录制"
            binding.spinnerCamera.isEnabled = !value
        }
    private lateinit var binding: ActivityRecordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordBinding.inflate(layoutInflater)
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
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        adapter = CameraSpinnerAdapter()
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

        binding.btnRecord.setOnClickListener {
            if (!recording) {
                startRecord()
            } else {
                stopRecord()
            }
        }


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
                this@RecordActivity.camera = camera
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
                this@RecordActivity.session = session
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
    }

    private fun getSessionSurfaceList(): ArrayList<Surface> {
        val list = arrayListOf(binding.surfaceMain.surface)
        if (recorder.getSurface() != null) {
            list.add(recorder.getSurface()!!)
        }
        return list
    }

    private fun getCaptureSurfaceList(): ArrayList<Surface> {
        return getSessionSurfaceList()
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

        Timber.d("previewSize = $previewSize")
    }

    private var recordParams: RecorderParams? = null

    private fun prepareRecorder() {
        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".mp4"

        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val folder = File(dcim, "CameraUtil")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val path = "${folder.absolutePath}/$title"
        val params = RecorderParams(
            title,
            previewSize,
            30,
            30,
            44100,
            File(path),
            binding.root.display.rotation
        )

        if (!recorder.prepare(params)) {
            Timber.e("recorder prepare failed")
            return
        }
        if (recording) {
            recordParams = params
        }
    }

    private fun releaseRecorder() {
        recorder.release()
        recording = false
    }

    private fun startRecord() {
        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".mp4"

        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

//        val folder = File(dcim, "CameraUtil")
        val folder = filesDir
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val path = "${folder.absolutePath}/$title"
        val params = RecorderParams(
            title,
            previewSize,
            30,
            30,
            44100,
            File(path),
            binding.root.display.rotation
        )

        if (!recorder.prepare(params)) {
            Timber.e("recorder prepare failed")
            return
        }
        if (recording) {
            recordParams = params
        }

        closeSession()
        recording = recorder.start()
        createSession()

    }

    private fun stopRecord() {
        closeSession()
        recorder.stop()
        recorder.release()
        recording = false
        createSession()

        recordParams?.let { params ->
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, params.title)
                put(MediaStore.Video.Media.DATA, params.outputFile.absolutePath)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis())
                put(MediaStore.Video.Media.WIDTH, previewSize.width)
                put(MediaStore.Video.Media.HEIGHT, previewSize.height)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        }

    }


}