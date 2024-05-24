package com.zu.camerautil

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.computeImageReaderSize
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.databinding.ActivityMultiSurfaceBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.util.ImageConverter
import timber.log.Timber

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

        override fun onSurfaceSizeChanged(surface: Surface, surfaceWidth: Int, surfaceHeight: Int) {
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
                return CameraDevice.TEMPLATE_PREVIEW
            }

            override fun getSessionSurfaceList(): List<Surface> {
                imageReaderSize = computeImageReaderSize(
                    binding.cameraSelector.currentCamera.characteristics,
                    currentSize!!,
                    imageReaderFormat,
                    true,
                    0
                )

                Timber.d("imageReaderSize = $imageReaderSize")

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
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.SURFACE_VIEW
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
            cameraLogic.closeCamera()
            cameraLogic.openCamera(binding.cameraSelector.currentCamera)
        }

        binding.btnRestartSession.setOnClickListener {
            cameraLogic.closeSession()
            cameraLogic.createSession()
        }

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
            var reopenCamera = false
            if (camera.cameraID != openedCameraID) {
                reopenCamera = true
            }

            if (size != currentSize) {
                currentSize = size
                binding.surfaceMain.previewSize = currentSize!!
                binding.surface1.previewSize = currentSize!!
                if (imageReaderSize != size) {
                    imageReaderSize = size
                    initImageReaders()
                }
                reopenCamera = true
            }

            if  (fps != currentFps) {
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