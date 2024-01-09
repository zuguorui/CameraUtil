package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.computeImageReaderSize
import com.zu.camerautil.camera.computePreviewSize
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityMultiSurfaceBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.util.ImageConverter
import com.zu.camerautil.view.CameraSpinnerAdapter
import timber.log.Timber
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class MultiSurfaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultiSurfaceBinding

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private lateinit var cameraLogic: BaseCameraLogic

    private var openedCameraID: String? = null
    private var currentSize: Size? = null
    private var currentFps: FPS? = null

    private var imageReaderSize: Size? = null

    private var imageReader1: ImageReader? = null

    private var imageReader2: ImageReader? = null

    private val imageReaderFormat = ImageFormat.YUV_420_888

    private var imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }

    private var imageReaderHandler = Handler(imageReaderThread.looper)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiSurfaceBinding.inflate(layoutInflater)
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

                imageReaderSize = computeImageReaderSize(
                    binding.cameraSelector.currentCamera.characteristics,
                    currentSize!!,
                    imageReaderFormat,
                    true,
                    1
                )

                initImageReaders()

                val list = ArrayList<Surface>()
                list.add(binding.surfaceMain.surface)
                if (binding.swSurface1.isChecked) {
                    list.add(binding.surface1.surface)
                }
                if (binding.swImageReader1.isChecked) {
                    list.add(imageReader1!!.surface)
                }

                if (binding.swImageReader2.isChecked) {
                    list.add(imageReader2!!.surface)
                }

                return list
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
        binding.surfaceMain.surfaceStateListener = surfaceStateListener
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surface1.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surface1.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.swSurface1.setOnCheckedChangeListener { _, isChecked ->
            cameraLogic.closeSession()
            cameraLogic.createSession()
        }

        binding.swImageReader1.setOnCheckedChangeListener { _, isChecked ->
            cameraLogic.closeSession()
            cameraLogic.createSession()
        }

        binding.swImageReader2.setOnCheckedChangeListener { _, isChecked ->
            cameraLogic.closeSession()
            cameraLogic.createSession()
        }

        binding.btnRestartCamera.setOnClickListener {
            cameraLogic.closeDevice()
            cameraLogic.openDevice(binding.cameraSelector.currentCamera)
        }

        binding.btnRestartSession.setOnClickListener {
            cameraLogic.closeSession()
            cameraLogic.createSession()
        }

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
            if (camera.cameraID != openedCameraID || size != currentSize || fps != currentFps) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
                cameraLogic.closeDevice()
                cameraLogic.openDevice(camera)
            }
        }
    }

    private fun initImageReaders() {
        val imageReaderSize = imageReaderSize ?: Size(0, 0)
        if (imageReader1?.width != imageReaderSize.width || imageReader1?.height != imageReaderSize.height) {
            imageReader1?.close()
            imageReader1 = ImageReader.newInstance(
                imageReaderSize.width,
                imageReaderSize.height,
                imageReaderFormat,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: kotlin.run {
                        Timber.e("image 1 is null")
                        return@setOnImageAvailableListener
                    }
                    //val bitmap = convertYPlaneToBitmap(image)
                    val rotation = binding?.root?.display?.rotation ?: kotlin.run {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    // Timber.d("imageReader1 get a bitmap")
                    val bitmap = ImageConverter.convertYUV_420_888_to_bitmap(
                        image,
                        rotation,
                        binding.cameraSelector.currentCamera.lensFacing
                    )
                    image.close()
                    runOnUiThread {
                        binding.iv1.setImageBitmap(bitmap)
                    }
                }, imageReaderHandler)
            }
        }

        if (imageReader2?.width != imageReaderSize.width || imageReader2?.height != imageReaderSize.height) {
            imageReader2?.close()
            imageReader2 = ImageReader.newInstance(
                imageReaderSize.width,
                imageReaderSize.height,
                imageReaderFormat,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: kotlin.run {
                        Timber.e("image 2 is null")
                        return@setOnImageAvailableListener
                    }
                    //val bitmap = convertYPlaneToBitmap(image)
                    val rotation = binding?.root?.display?.rotation ?: kotlin.run {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    // Timber.d("imageReader2 get a bitmap")
                    val bitmap = ImageConverter.convertYUV_420_888_to_bitmap(
                        image,
                        rotation,
                        binding.cameraSelector.currentCamera.lensFacing
                    )
                    image.close()
                    runOnUiThread {
                        binding.iv2.setImageBitmap(bitmap)
                    }
                }, imageReaderHandler)
            }
        }
    }

}