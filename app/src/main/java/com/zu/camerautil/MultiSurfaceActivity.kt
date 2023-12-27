package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
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
import android.view.TextureView
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.bean.CameraInfoWrapper
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

    private val cameraManager: CameraManager by lazy {
        ((getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private var surfaceCreated = false
    private var openCameraID: String? = null

    private var camera: CameraDevice? = null

    private var session: CameraCaptureSession? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private lateinit var previewSize: Size

    private lateinit var imageReaderSize: Size

    private var imageReader1: ImageReader? = null

    private var imageReader2: ImageReader? = null

    private val imageReaderFormat = ImageFormat.YUV_420_888

    private var cameraThread = HandlerThread("CameraThread").apply { start() }

    private var cameraHandler = Handler(cameraThread.looper)

    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private var imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }

    private var imageReaderHandler = Handler(imageReaderThread.looper)


    private val textureCallback = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            surfaceCreated = true
            val cameraID = selectCameraID(cameraInfoMap, CameraCharacteristics.LENS_FACING_BACK, true)
            binding.spinnerCamera.setSelection(cameraList.indexOfFirst { info -> info.cameraID == cameraID })
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Timber.d("textureSizeChanged, width = $width, height = $height")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Timber.d("textureDestroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            //Timber.d("textureUpdated")
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceCreated = true
            Timber.d("surfaceCreated: Thread = ${Thread.currentThread().name}")
            val cameraID = selectCameraID(cameraInfoMap, CameraCharacteristics.LENS_FACING_BACK, true)
            binding.spinnerCamera.setSelection(cameraList.indexOfFirst { info -> info.cameraID == cameraID })
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            val surfaceSize = binding.surfaceMain.surfaceSize
            Timber.d("surfaceChanged: surfaceSize = $surfaceSize, ratio = ${surfaceSize.toRational()}")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {

        }
    }

    private val surfaceStateListener = object : PreviewViewImplementation.SurfaceStateListener {
        override fun onSurfaceCreated(surface: Surface) {
            surfaceCreated = true
            Timber.d("surfaceCreated: Thread = ${Thread.currentThread().name}")
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

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {

        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            Timber.d("onCaptureFailed: $failure")
        }
    }

    private lateinit var adapter: CameraSpinnerAdapter
    private val cameraList: ArrayList<CameraInfoWrapper> by lazy {
        ArrayList<CameraInfoWrapper>().apply {
            addAll(cameraInfoMap.values)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiSurfaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        openCameraID = null
        surfaceCreated = false
        initViews()
    }

    override fun onDestroy() {
        closeDevice()
        super.onDestroy()
    }

    private fun initViews() {
        binding.surfaceMain.surfaceStateListener = surfaceStateListener
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surface1.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surface1.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.swSurface1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                addSurface(binding.surface1.surface)
            } else {
                removeSurface(binding.surface1.surface)
            }
        }

        binding.swImageReader1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                addSurface(imageReader1!!.surface)
            } else {
                removeSurface(imageReader1!!.surface)
            }
        }

        binding.swImageReader2.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                addSurface(imageReader2.surface)
//            } else {
//                removeSurface(imageReader2.surface)
//            }
        }

        binding.btnRestartCamera.setOnClickListener {
            val cameraID = openCameraID
            closeDevice()
            if (cameraID != null) {
                openDevice(cameraID!!)
            }

        }

        binding.btnRestartSession.setOnClickListener {
            closeSession()
            createSession()
        }

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
                if (surfaceCreated) {
                    openDevice(cameraID!!)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                closeDevice()

            }
        }
    }

    private fun initImageReaders() {
        val imageReaderSize = imageReaderSize ?: return
        if (imageReader1?.width != imageReaderSize.width || imageReader1?.height != imageReaderSize.height) {
            imageReader1?.close()
            imageReader1 = ImageReader.newInstance(imageReaderSize.width, imageReaderSize.height, imageReaderFormat, 2).apply {
                setOnImageAvailableListener({reader ->
                    val image = reader.acquireLatestImage() ?: kotlin.run {
                        Timber.e("image 1 is null")
                        return@setOnImageAvailableListener
                    }
                    //val bitmap = convertYPlaneToBitmap(image)
                    val bitmap = ImageConverter.convertYUV_420_888_to_bitmap(image)
                    image.close()
                    runOnUiThread {
                        binding.iv1.setImageBitmap(bitmap)
                    }
                }, imageReaderHandler)
            }
        }

        if (imageReader2?.width != imageReaderSize.width || imageReader2?.height != imageReaderSize.height) {
            imageReader2?.close()
            imageReader2 = ImageReader.newInstance(imageReaderSize.width, imageReaderSize.height, imageReaderFormat, 2).apply {
                setOnImageAvailableListener({reader ->
                    val image = reader.acquireLatestImage() ?: kotlin.run {
                        Timber.e("image 2 is null")
                        return@setOnImageAvailableListener
                    }
                    //val bitmap = convertYPlaneToBitmap(image)
                    val bitmap = ImageConverter.convertYUV_420_888_to_bitmap(image)
                    image.close()
                    runOnUiThread {
                        binding.iv2.setImageBitmap(bitmap)
                    }
                }, imageReaderHandler)
            }
        }
    }


    private fun openDevice(cameraID: String) {
        if (openCameraID == cameraID) {
            Timber.d("same cameraID, return")
        }
        if (camera != null) {
            closeDevice()
        }

        openCameraID = cameraID

        val characteristics = cameraInfoMap[cameraID]!!.characteristics
        computeSizes(characteristics)
        // 获取最接近容器宽高比的Camera输出，并且设置给SurfaceView
        binding.surfaceMain.previewSize = previewSize
        binding.surface1.previewSize = previewSize
        initImageReaders()

        val info = cameraInfoMap[openCameraID]!!
        val finalID = info.logicalID ?: openCameraID!!
        Timber.d("openDevice $finalID")
        cameraManager.openCamera(finalID, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@MultiSurfaceActivity.camera = camera
                checkSupport(cameraInfoMap[openCameraID]!!.characteristics)
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
        openCameraID = null
    }

    private fun createSession() {
        val camera = this.camera ?: return

        val createSessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                this@MultiSurfaceActivity.session = session
                startPreview()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exception = RuntimeException("create session failed")
                Timber.e("onConfigureFailed: ${exception.message}")
            }
        }

        val target = getSessionSurfaceList()

        for (surface in target) {
            Timber.d("sessionSurface: $surface")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = cameraInfoMap[openCameraID]!!
            val outputConfigurations = ArrayList<OutputConfiguration>()
            for (surface in target) {
                val outputConfiguration = OutputConfiguration(surface)
                if (info.logicalID != null) {
                    Timber.w("camera${info.cameraID} belong to logical camera${info.logicalID}, set physical camera")
                    outputConfiguration.setPhysicalCameraId(info.cameraID)
                }
                outputConfigurations.add(outputConfiguration)
            }
            val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, cameraExecutor, createSessionCallback)
            if (Build.VERSION.SDK_INT >= 29) {
                if (!camera.isSessionConfigurationSupported(sessionConfiguration)) {
                    Timber.e("camera not support session configuration")
                }
            }
            camera.createCaptureSession(sessionConfiguration)
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
        val list = ArrayList<Surface>()
        list.add(binding.surfaceMain.surface)
        list.add(binding.surface1.surface)
        list.add(imageReader1!!.surface)
        //list.add(imageReader2!!.surface)

        return list
    }

    private fun getCaptureSurfaceList(): ArrayList<Surface> {
        val list = ArrayList<Surface>()
        list.add(binding.surfaceMain.surface)
        if (binding.swSurface1.isChecked) {
            list.add(binding.surface1.surface)
        }
        if (binding.swImageReader1.isChecked) {
            list.add(imageReader1!!.surface)
        }
//        if (binding.swImageReader2.isChecked) {
//            list.add(imageReader2!!.surface)
//        }

        return list
    }

    private fun addSurface(surface: Surface) {
        val builder = captureRequestBuilder ?: return
        val session = this.session ?: return
        builder.addTarget(surface)
        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    private fun removeSurface(surface: Surface) {
        val builder = captureRequestBuilder ?: return
        val session = this.session ?: return
        builder.removeTarget(surface)
        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    /**
     * 根据当前选中的镜头，查询其支持的分辨率。
     * 使宽高比与view宽高比接近
     * 使预览分辨率与预览窗口大小接近
     * 使分析图片的分辨率最小以达到最快速度
     * 要同时查询支持Surface的分辨率及ImageReader的分辨率，两者宽高比要一致才行，否则分析会失败。
     *
     * */
    private fun computeSizes(characteristics: CameraCharacteristics) {
        val viewSize = with(binding.surfaceMain) {
            Size(width, height)
        }
        previewSize = computePreviewSize(characteristics, viewSize, binding.root.display.rotation,
            SurfaceHolder::class.java)

        imageReaderSize = computeImageReaderSize(characteristics, previewSize,
            true, -1) ?: throw RuntimeException("No reader size")
        //imageReaderSize = Size(320, 240)

        Timber.d("previewViewSize: $viewSize, ratio: ${viewSize.toRational()}")
        Timber.d("previewSize: $previewSize, ratio: ${previewSize.toRational()}")
        Timber.d("analysisSize: $imageReaderSize, ratio: ${imageReaderSize.toRational()}")
    }

    private fun checkSupport(characteristics: CameraCharacteristics) {
        val target = getSessionSurfaceList()
        val configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        for (surface in target) {
            if (!configurationMap.isOutputSupportedFor(surface)) {
                Timber.e("Not support for surface $surface")
            }
        }
    }

}