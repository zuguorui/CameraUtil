package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.ContentValues
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraUsage
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.CoroutineCameraLogic
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityRecordBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.recorder.IRecorder
import com.zu.camerautil.recorder.RecorderParams
import com.zu.camerautil.recorder.SystemRecorder
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

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

    private var recordParams: RecorderParams? = null

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
        cameraLogic.closeCamera()
        recorder.release()
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
                return if (recording) {
                    CameraUsage.RECORD
                } else {
                    CameraUsage.PREVIEW
                }
            }

            override fun getSessionSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                if (recorder.isReady) {
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
            var reopenCamera = false

            if (camera.cameraID != openedCameraID) {
                reopenCamera = true
            }

            if (size != currentSize) {
                currentSize = size
                binding.surfaceMain.previewSize = size
                reopenCamera = true
            }

            if (fps != currentFps) {
                currentFps = fps
                reopenCamera = true
            }

            if (reopenCamera) {
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

    private fun startRecord() {
        val size = currentSize ?: return
        val fps = currentFps ?: return
        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".mp4"
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val folder = File(dcim, "CameraUtil")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val path = "${folder.absolutePath}/$title"
        val params = RecorderParams(
            title,
            size,
            fps.value,
            fps.value,
            44100,
            File(path),
            binding.root.display.rotation
        )

        if (!recorder.prepare(params)) {
            Timber.e("recorder prepare failed")
            return
        }
        recordParams = params

        cameraLogic.closeSession()
        recording = recorder.start()
        cameraLogic.createSession()

    }

    private fun stopRecord() {
        val previousParams = recordParams
        cameraLogic.closeSession()
        recorder.stop()
        recorder.release()
        recording = false
        cameraLogic.createSession()
        previousParams?.let { params ->
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