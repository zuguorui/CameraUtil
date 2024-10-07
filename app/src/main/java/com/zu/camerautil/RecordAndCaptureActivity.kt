package com.zu.camerautil

import android.content.ContentValues
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.computeRotation
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityRecordAndCaptureBinding
import com.zu.camerautil.logic.CameraControlLogic
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.recorder.CodecRecorder
import com.zu.camerautil.recorder.IRecorder
import com.zu.camerautil.recorder.RecorderParams
import com.zu.camerautil.recorder.SystemRecorder
import com.zu.camerautil.util.createPictureUri
import com.zu.camerautil.util.createVideoPath
import com.zu.camerautil.util.createVideoUri
import com.zu.camerautil.util.printTimeCost
import com.zu.camerautil.util.refreshCameraParams
import com.zu.camerautil.util.setFlashMode
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

class RecordAndCaptureActivity : AppCompatActivity() {

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic
    private lateinit var controlLogic: CameraControlLogic

    private var recordParams: RecorderParams? = null

    private var aeMeasureState = AeMeasureState.IDLE

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

    private val captureCallback = object : CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE) ?: -1
            //Timber.d("aeState: $aeState, aeMeasureState: $aeMeasureState")
            when (aeMeasureState) {
                AeMeasureState.WAITING_START -> {
                    when (aeState) {
                        -1 -> {
                            realTakePicture()
                        }
                        CameraMetadata.CONTROL_AE_STATE_PRECAPTURE,
                        CameraMetadata.CONTROL_AE_STATE_CONVERGED -> {
                            aeMeasureState = AeMeasureState.WAITING_END
                        }
                    }
                }
                AeMeasureState.WAITING_END -> {
                    when (aeState) {
                        -1 -> {
                            realTakePicture()
                        }
                        CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED,
                        CameraMetadata.CONTROL_AE_STATE_CONVERGED -> {
                            realTakePicture()
                        }
                    }
                }
                AeMeasureState.IDLE -> {

                }
            }
        }
    }

    private var imageReader: ImageReader? = null

    private var recorder: IRecorder = CodecRecorder()
    private var recording = false
        set(value) {
            field = value
            binding.btnRecord.text = if (value) "停止录制" else "开始录制"
            binding.cameraLens.setEnable(!value)
        }

    private lateinit var binding: ActivityRecordAndCaptureBinding

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordAndCaptureBinding.inflate(layoutInflater)
        handlerThread = HandlerThread("RecordActivity-capture")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        setContentView(binding.root)
        initControlLogic()
        initViews()
    }

    private fun initControlLogic() {
        val configCallback = object : CameraControlLogic.ConfigCallback {

            override fun getTemplate(): Int {
                return if (recording) {
                    CameraDevice.TEMPLATE_RECORD
                } else {
                    CameraDevice.TEMPLATE_PREVIEW
                }
//                return CameraDevice.TEMPLATE_RECORD
            }

            override fun getSessionSurfaceList(): List<Surface> {
                var surfaceList = arrayListOf(binding.surfaceMain.surface)
                if (recorder.isReady) {
                    val recordSurface = printTimeCost {
                        recorder.getSurface()!!
                    }
                    surfaceList.add(recordSurface)
                } else {
                    Timber.w("record is not ready")
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
        cameraLogic = BaseCameraLogic(this)
        controlLogic = CameraControlLogic(cameraLogic, binding.cameraLens, binding.cameraParams, configCallback)

        controlLogic.cameraConfigListener = object : CameraControlLogic.CameraConfigListener {
            override fun onCameraConfigChanged(
                cameraID: String,
                size: Size,
                fps: FPS,
                reopen: Boolean
            ) {
                Timber.d("onCameraConfigChanged: cameraID: $cameraID, size: $size, fps: $fps, reopen: $reopen")
                binding.surfaceMain.previewSize = size
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
        }

        controlLogic.captureCallback = captureCallback
    }

    override fun onDestroy() {
        handlerThread.quitSafely()
        cameraLogic.closeCamera()
        recorder.release()
        super.onDestroy()
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
    }

    private fun startRecord() {
        val size = controlLogic.currentSize ?: return
        val fps = controlLogic.currentFps ?: return
        val camera = controlLogic.currentCameraID?.let {
            cameraInfoMap[it] ?: return
        } ?: return
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date(System.currentTimeMillis()))
        val title = "${size.width}x${size.height}.${fps.value}FPS.$timeStamp.mp4"

        val saveUri = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createVideoUri(this, title) ?: kotlin.run {
                Timber.e("create uri failed")
                recording = false
                return
            }
        } else {
            null
        }

        val savePath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            createVideoPath(title)
        } else {
            null
        }


        val params = RecorderParams(
            title = title,
            resolution = size,
            inputFps = fps.value,
            outputFps = fps.value,
            sampleRate = 44100,
            outputPath = savePath,
            outputUri = saveUri,
            viewOrientation = binding.root.display.rotation,
            sensorOrientation = camera.sensorOrientation!!,
            facing = cameraInfoMap[controlLogic.currentCameraID!!]!!.lensFacing
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
        val flashMode = binding.cameraParams.getParamValue(CameraParamID.FLASH_MODE) as FlashUtil.FlashMode
        if (flashMode == FlashUtil.FlashMode.ON || flashMode == FlashUtil.FlashMode.AUTO) {
            startMeasureAe()
            return
        }
        realTakePicture()
    }

    private fun startMeasureAe() {
        if (aeMeasureState != AeMeasureState.IDLE) {
            return
        }
        aeMeasureState = AeMeasureState.WAITING_START
        val camera = cameraLogic.camera ?: return
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(binding.surfaceMain.surface)
        refreshCameraParams(binding.cameraParams, builder)
        setFlashMode(binding.cameraParams.getParamValue(CameraParamID.FLASH_MODE) as FlashUtil.FlashMode, builder)
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
        cameraLogic.updateSession { session ->
            session.capture(
                builder.build(),
                captureCallback,
                handler)
        }
    }


    private fun realTakePicture() {

        aeMeasureState = AeMeasureState.IDLE

        val size = controlLogic.currentSize ?: return
        val reader = imageReader ?: return

        cameraLogic.updateSession { session ->
            val camera = cameraInfoMap[controlLogic.currentCameraID!!]!!
            // 这里的template最好和预览时给的一致。否则某些手机在拍照时会出现问题。例如画幅不一样、画面不一致等
            val request = session.device.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT)
            request.set(CaptureRequest.JPEG_ORIENTATION, computeRotation(camera.sensorOrientation, binding.root.display.rotation * 90, camera.lensFacing))
            request.set(CaptureRequest.JPEG_QUALITY, 90)
            request.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            request.addTarget(binding.surfaceMain.surface)
            request.addTarget(reader.surface)

            setFlashMode(binding.cameraParams.getParamValue(CameraParamID.FLASH_MODE) as FlashUtil.FlashMode, request)
            refreshCameraParams(binding.cameraParams, request)
            //session.stopRepeating()
            session.capture(
                request.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        resumePreview()
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        super.onCaptureFailed(session, request, failure)
                        resumePreview()
                    }

                    fun resumePreview() {
                        //cameraLogic.startRepeating()
                    }
                },
                handler)
        }
    }

    private fun savePicture(image: Image) {
        val data = image.planes[0].buffer
        Timber.d("savePicture: imageReader.size = [${imageReader?.width}, ${imageReader?.height}], image.size = [${image.width}, ${image.height}]")
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

            ByteArrayInputStream(buffer).use {
                val exifInterface = ExifInterface(it)
                val jpegWidth = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                val jpegHeight = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
                Timber.d("jpeg.size = [$jpegWidth, $jpegHeight]")
            }
        }
    }

    private enum class AeMeasureState {
        IDLE,
        WAITING_START,
        WAITING_END,
    }



}