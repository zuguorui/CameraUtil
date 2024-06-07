package com.zu.camerautil

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Rational
import android.util.Size
import android.view.Surface
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityCaptureBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import timber.log.Timber
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

class CaptureActivity : AppCompatActivity() {

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
            binding.cameraLens.setCameras(cameraInfoMap.values)
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

    private lateinit var binding: ActivityCaptureBinding

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
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

            override fun getTemplate(): Int {
                return CameraDevice.TEMPLATE_STILL_CAPTURE
            }

            override fun getSessionSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                imageReader?.let {
                    surfaceList.add(it.surface)
                }
                return surfaceList
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                return surfaceList
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                binding.cameraParams.getParamValue(CameraParamID.WB_MODE)?.let {
                    requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, it as Int)
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
                if (binding.cameraParams.isParamAuto(CameraParamID.SEC)) {
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { sec ->
                        runOnUiThread {
                            binding.cameraParams.setParamValue(CameraParamID.SEC, sec)
                        }
                    }
                }

                if (binding.cameraParams.isParamAuto(CameraParamID.ISO)) {
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { iso ->
                        runOnUiThread {
                            binding.cameraParams.setParamValue(CameraParamID.ISO, iso)
                        }
                    }
                }

//                result.get(CaptureResult.CONTROL_AWB_MODE)?.let { wbMode ->
//                    if (wbMode != binding.cameraParams.getParamValue(CameraParamID.WB_MODE)) {
//                        runOnUiThread {
//                            binding.cameraParams.setParamValue(CameraParamID.WB_MODE, wbMode)
//                        }
//                    }
//                }

            }
        }
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.btnCapture.setOnClickListener {
            takePicture()
        }

        binding.cameraLens.onConfigChangedListener = {camera, fps, size ->
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

            binding.cameraParams.setCameraConfig(camera, size, fps)
        }

        binding.cameraParams.addAutoModeListener(CameraParamID.SEC) { secAuto ->
            val isoAuto = binding.cameraParams.isParamAuto(CameraParamID.ISO)
            if (secAuto != isoAuto) {
                binding.cameraParams.setParamAuto(CameraParamID.ISO, secAuto)
                setSecAndISOAuto(secAuto)
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.SEC) {
            if (!binding.cameraParams.isParamAuto(CameraParamID.SEC)) {
                val sec = it as? Long ?: return@addValueListener
                val rational = Rational(1_000_000_000, sec.toInt())
                Timber.d("secChange: $rational")
                cameraLogic.updateCaptureRequestParams { builder ->
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sec)
                }
            }
        }

        binding.cameraParams.addAutoModeListener(CameraParamID.ISO) { isoAuto ->
            val secAuto = binding.cameraParams.isParamAuto(CameraParamID.SEC)
            if (secAuto != isoAuto) {
                binding.cameraParams.setParamAuto(CameraParamID.SEC, isoAuto)
                setSecAndISOAuto(isoAuto)
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.ISO) {
            if (!binding.cameraParams.isParamAuto(CameraParamID.ISO)) {
                val iso = it as? Int ?: return@addValueListener
                cameraLogic.updateCaptureRequestParams { builder ->
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                }
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.WB_MODE) { mode ->
            val wbMode = mode as? Int ?: return@addValueListener
            val isManual = mode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
            cameraLogic.updateCaptureRequestParams { requestBuilder ->
                requestBuilder.apply {
                    set(CaptureRequest.CONTROL_AWB_MODE, wbMode)
                }
            }
        }

        binding.cameraParams.addValueListener(CameraParamID.FLASH_MODE) { mode ->
            val flashMode = mode as? FlashUtil.FlashMode ?: return@addValueListener
            cameraLogic.updateCaptureRequestParams { builder ->
                setFlashMode(flashMode, builder)
            }
        }

    }

    private fun setFlashMode(flashMode: FlashUtil.FlashMode, builder: CaptureRequest.Builder) {
        when (flashMode) {
            FlashUtil.FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashUtil.FlashMode.ON -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashUtil.FlashMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashUtil.FlashMode.TORCH -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
        }
    }

    private fun setSecAndISOAuto(auto: Boolean) {
        if (auto) {
            cameraLogic.updateCaptureRequestParams { builder ->
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
        } else {
            cameraLogic.updateCaptureRequestParams { builder ->
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            }
        }
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

    private fun takePicture() {
        val size = currentSize ?: return
        val reader = imageReader ?: return
        val camera = cameraInfoMap[openedCameraID] ?: return

        cameraLogic.updateSession {
            val request = it.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            request.set(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
            request.set(CaptureRequest.JPEG_ORIENTATION, getRotation())
            request.addTarget(reader.surface)
            request.addTarget(binding.surfaceMain.surface)
            //setFlashMode(binding.cameraParams.getParamValue(CameraParamID.FLASH_MODE) as FlashUtil.FlashMode, request)
            //it.stopRepeating()
            it.capture(
                request.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        super.onCaptureFailed(session, request, failure)
                    }
                },
                handler)
        }
    }

    private fun getRotation(): Int {
        val camera = cameraInfoMap[openedCameraID] ?: return 0
        val isFront = camera.lensFacing == CameraCharacteristics.LENS_FACING_FRONT
        val rotationSign = if (isFront) 1 else -1
        val sensorOrientation = camera.sensorOrientation!!
        val viewOrientation = binding.root.display.rotation
        val rotation = (sensorOrientation - viewOrientation * rotationSign + 360) % 360
        return rotation
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