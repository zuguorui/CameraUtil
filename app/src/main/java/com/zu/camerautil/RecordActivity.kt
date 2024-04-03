package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaCodec
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
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
import java.io.FileOutputStream
import java.io.OutputStreamWriter
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

    private var imageReader: ImageReader? = null

    private var recorder: IRecorder = SystemRecorder()
    private var recording = false
        set(value) {
            field = value
            binding.btnRecord.text = if (value) "停止录制" else "开始录制"
            binding.cameraSelector.setEnable(!value)
        }
    private lateinit var binding: ActivityRecordBinding

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordBinding.inflate(layoutInflater)
        handlerThread = HandlerThread("RecordActivity-capture")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        setContentView(binding.root)
        initCameraLogic()
        initViews()
    }

    override fun onDestroy() {
        handlerThread.quitSafely()
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
                imageReader?.let {
                    surfaceList.add(it.surface)
                }
                return surfaceList
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                if (recorder.isReady) {
                    surfaceList.add(recorder.getSurface()!!)
                }
                return surfaceList
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

        binding.btnTakePicture.setOnClickListener {
            takePicture()
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
                if (imageReader != null && (imageReader!!.width != size.width || imageReader!!.height != size.height)) {
                    imageReader?.close()
                    imageReader = null
                }
                if (imageReader == null) {
                    imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1).apply {
                        setOnImageAvailableListener({ reader ->
                            val image = reader.acquireLatestImage()
                            savePicture(image)
                            image.close()
                        }, handler)
                    }

                }
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
        val camera = openedCameraID?.let {
            cameraInfoMap[it] ?: return
        } ?: return
        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".mp4"

        val saveUri = createVideoUri(this, title) ?: kotlin.run {
            Timber.e("create uri failed")
            recording = false
            return
        }


        val params = RecorderParams(
            title = title,
            resolution = size,
            inputFps = fps.value,
            outputFps = fps.value,
            sampleRate = 44100,
            outputFile = null,
            outputUri = saveUri,
            viewOrientation = binding.root.display.rotation,
            sensorOrientation = camera.sensorOrientation!!,
            facing = cameraInfoMap[openedCameraID!!]!!.lensFacing
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

    private fun createVideoUri(context: Context, name: String, isPending: Boolean = false): Uri? {
        val DCIM = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val folderPath = "$DCIM/CameraUtil/video/"
        val relativePath = folderPath.substring(folderPath.indexOf("DCIM"))
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.TITLE, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATA, "$folderPath$name")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            if (isPending) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        contentValues.run {
            Timber.d("""
                    createVideoUri:
                        display_name = ${get(MediaStore.Video.Media.DISPLAY_NAME)}
                        title = ${get(MediaStore.Video.Media.TITLE)}
                        mime_type = ${get(MediaStore.Video.Media.MIME_TYPE)}
                        data = ${get(MediaStore.Video.Media.DATA)}
                        relative_path = ${get(MediaStore.Video.Media.RELATIVE_PATH)}
                """.trimIndent())
        }
        var collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collectionUri, contentValues)
        return uri
    }

    private fun createPictureUri(context: Context, name: String, isPending: Boolean = false): Uri? {
        val DCIM = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val folderPath = "$DCIM/CameraUtil/picture/"
        val relativePath = folderPath.substring(folderPath.indexOf("DCIM"))
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.TITLE, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, "$folderPath$name")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            if (isPending) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        contentValues.run {
            Timber.d("""
                    createPictureUri:
                        display_name = ${get(MediaStore.Images.Media.DISPLAY_NAME)}
                        title = ${get(MediaStore.Images.Media.TITLE)}
                        mime_type = ${get(MediaStore.Images.Media.MIME_TYPE)}
                        data = ${get(MediaStore.Images.Media.DATA)}
                        relative_path = ${get(MediaStore.Images.Media.RELATIVE_PATH)}
                """.trimIndent())
        }
        var collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collectionUri, contentValues)
        return uri
    }

    private fun stopRecord() {
        val previousParams = recordParams
        cameraLogic.closeSession()
        recorder.stop()
        recorder.release()
        recording = false
        cameraLogic.createSession()
        previousParams?.let { params ->
            if (params.outputUri == null) {
                return@let
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(params.outputUri!!, contentValues, null, null)
        }
    }

    private fun takePicture() {
        val size = currentSize ?: return
        val reader = imageReader ?: return

        cameraLogic.updateSession {
            val request = it.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            request.addTarget(reader.surface)
            it.capture(request.build(), null, null)
        }
    }

    private fun savePicture(image: Image) {
        val data = image.planes[0].buffer
        val title = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis())) + ".jpg"
        val uri = createPictureUri(this, title) ?: kotlin.run {
            Timber.e("create uri failed")
            return
        }
        val descriptor = contentResolver.openFileDescriptor(uri, "w", null) ?: kotlin.run {
            Timber.e("open file descriptor failed")
            return
        }
        FileOutputStream(descriptor.fileDescriptor).use {
            val buffer = ByteArray(data.limit())
            data.get(buffer)
            it.write(buffer)
        }
    }


}