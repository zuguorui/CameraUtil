package com.zu.camerautil

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
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
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
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

        override fun onSurfaceSizeChanged(surface: Surface, width: Int, height: Int) {
            val surfaceSize = binding.surfaceMain.surfaceSize
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun onSurfaceDestroyed(surface: Surface) {

        }
    }
    // camera objects end

    private var recorder: IRecorder = SystemRecorder()
    private var recording = false
        set(value) {
            field = value
            binding.btnRecord.text = if (value) "停止录制" else "开始录制"
            binding.cameraSelector.setEnable(!value)
        }
    private lateinit var binding: ActivityRecordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordBinding.inflate(layoutInflater)
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
                if (currentSize !=  binding.cameraSelector.currentSize) {
                    currentSize = binding.cameraSelector.currentSize
                    binding.surfaceMain.previewSize = currentSize!!
                }
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                if (recording) {
                    surfaceList.add(recorder.getSurface()!!)
                }
                return surfaceList
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
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

        binding.btnRecord.setOnClickListener {
            if (!recording) {
                startRecord()
            } else {
                stopRecord()
            }
        }

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->

            if (camera.cameraID != openedCameraID || size != currentSize || fps != currentFps) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
                cameraLogic.closeDevice()
                cameraLogic.openDevice(camera)
            }
        }

    }

    private var recordParams: RecorderParams? = null

    private fun prepareRecorder() {
        val currentSize = currentSize ?: return
        val currentFps = currentFps ?: return

        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".mp4"

        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val folder = File(dcim, "CameraUtil")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val path = "${folder.absolutePath}/$title"
        val params = RecorderParams(
            title,
            currentSize,
            currentFps.value,
            currentFps.value,
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
        val currentSize = currentSize ?: return
        val currentFps = currentFps ?: return

        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".mp4"
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val folder = File(dcim, "CameraUtil")
//        val folder = filesDir
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val path = "${folder.absolutePath}/$title"
        val params = RecorderParams(
            title,
            currentSize,
            currentFps.value,
            currentFps.value,
            44100,
            File(path),
            binding.root.display.rotation
        )

        if (!recorder.prepare(params)) {
            Timber.e("recorder prepare failed")
            return
        }


        cameraLogic.closeSession()
        recording = recorder.start()
        if (recording) {
            recordParams = params
        }
        cameraLogic.createSession()

    }

    private fun stopRecord() {
        cameraLogic.closeSession()
        recorder.stop()
        recorder.release()
        recording = false
        cameraLogic.createSession()

        recordParams?.let { params ->
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, params.title)
                put(MediaStore.Video.Media.DATA, params.outputFile.absolutePath)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis())
                put(MediaStore.Video.Media.WIDTH, params.resolution.width)
                put(MediaStore.Video.Media.HEIGHT, params.resolution.height)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        }
    }


}