package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.databinding.ActivityMultiSurfaceBinding
import com.zu.camerautil.view.preview.Camera2PreviewView
import timber.log.Timber
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.abs

@SuppressLint("MissingPermission")
class MultiSurfaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultiSurfaceBinding

    private val cameraManager: CameraManager by lazy {
        ((getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    private val cameraInfoMap = HashMap<String, CameraInfoWrapper>()

    private val cameraID: String by lazy {
        var id = selectBackCameraID()
        id = "3"
        Timber.d("cameraID: $id")
        id
    }

    private val characteristics: CameraCharacteristics
        get() = cameraInfoMap[cameraID]!!.characteristics

    private var camera: CameraDevice? = null

    private var session: CameraCaptureSession? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private lateinit var previewSize: Size

    private lateinit var imageReaderSize: Size

    private lateinit var imageReader1: ImageReader

    private lateinit var imageReader2: ImageReader

    private val imageReaderFormat = ImageFormat.YUV_420_888

    private var cameraThread = HandlerThread("CameraThread").apply { start() }

    private var cameraHandler = Handler(cameraThread.looper)

    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private var imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }

    private var imageReaderHandler = Handler(imageReaderThread.looper)

    private lateinit var mainSurface: Surface

    private val textureCallback = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            computeSizes()
            initComponents()
            Timber.d("textureAvailable, width = $width, height = $height")
            surface.setDefaultBufferSize(previewSize.width, previewSize.height)
            mainSurface = Surface(surface)
            binding.surface1.setSourceResolution(previewSize.width, previewSize.height)
            Timber.d("surfaceCreated: select cameraOutputSize: w * h = ${previewSize.width} * ${previewSize.height}")
            binding.root.post {
                openDevice()
            }
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
            // 获取最接近容器宽高比的Camera输出，并且设置给SurfaceView
            computeSizes()
            initComponents()
            // binding.surfaceMain.setSourceResolution(previewSize.width, previewSize.height)
            binding.surface1.setSourceResolution(previewSize.width, previewSize.height)
            Timber.d("surfaceCreated: select cameraOutputSize: w * h = ${previewSize.width} * ${previewSize.height}")
            binding.root.post {
                openDevice()
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Timber.d("surfaceChanged: w * h = $width * $height, ratio = ${width.toFloat() / height}")
            Timber.d("surfaceChanged: surfaceView: w * h = ${binding.surfaceMain.width} * ${binding.surfaceMain.height}, ratio = ${binding.surfaceMain.run { width.toFloat() / height }}")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {

        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiSurfaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
    }

    private fun initViews() {
//        binding.surfaceMain.holder.addCallback(surfaceCallback)
//        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceTextureListener = textureCallback

        binding.surface1.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.swSurface1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                addSurface(binding.surface1.surface)
            } else {
                removeSurface(binding.surface1.surface)
            }
        }

        binding.swImageReader1.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                addSurface(imageReader1.surface)
//            } else {
//                removeSurface(imageReader1.surface)
//            }
        }

        binding.swImageReader2.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                addSurface(imageReader2.surface)
//            } else {
//                removeSurface(imageReader2.surface)
//            }
        }

        binding.btnRestartCamera.setOnClickListener {
            closeDevice()
            openDevice()
        }

        binding.btnRestartSession.setOnClickListener {
            closeSession()
            createSession()
        }
    }

    private fun initComponents() {
        val imageReaderSize = imageReaderSize ?: return
        imageReader1 = ImageReader.newInstance(imageReaderSize.width, imageReaderSize.height, imageReaderFormat, 2).apply {
            setOnImageAvailableListener({reader ->
                val image = reader.acquireLatestImage() ?: kotlin.run {
                    Timber.e("image 1 is null")
                    return@setOnImageAvailableListener
                }
                val bitmap = convertYPlaneToBitmap(image)
                image.close()
                runOnUiThread {
                    binding.iv1.setImageBitmap(bitmap)
                }
            }, imageReaderHandler)
        }

        imageReader2 = ImageReader.newInstance(imageReaderSize.width, imageReaderSize.height, imageReaderFormat, 2).apply {
            setOnImageAvailableListener({reader ->
                val image = reader.acquireLatestImage() ?: kotlin.run {
                    Timber.e("image 2 is null")
                    return@setOnImageAvailableListener
                }
                val bitmap = convertYPlaneToBitmap(image)
                image.close()
                runOnUiThread {
                    binding.iv2.setImageBitmap(bitmap)
                }
            }, imageReaderHandler)
        }
    }


    private fun openDevice() {
        if (camera != null) {
            closeDevice()
        }
        val info = cameraInfoMap[cameraID]!!
        val finalID = info.logicalID ?: cameraID
        cameraManager.openCamera(finalID, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@MultiSurfaceActivity.camera = camera
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = cameraInfoMap[cameraID]!!
            val finalID = info.logicalID ?: info.cameraID
            val outputConfigurations = ArrayList<OutputConfiguration>()
            for (surface in target) {
                val outputConfiguration = OutputConfiguration(surface)
                if (info.logicalID != null) {
                    Timber.w("camera${info.cameraID} belong to logical camera${info.logicalID}, set physical camera")
                    outputConfiguration.setPhysicalCameraId(cameraID)
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

            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(60, 60))

            getCaptureSurfaceList().forEach {
                addTarget(it)
            }
        }

        session.setRepeatingRequest(captureRequestBuilder!!.build(), captureCallback, cameraHandler)
    }

    private fun getSessionSurfaceList(): ArrayList<Surface> {
        val list = ArrayList<Surface>()
        list.add(mainSurface)
        list.add(binding.surface1.surface)
//        list.add(imageReader1.surface)
        //list.add(imageReader2.surface)

        return list
    }

    private fun getCaptureSurfaceList(): ArrayList<Surface> {
        val list = ArrayList<Surface>()
        list.add(mainSurface)
        if (binding.swSurface1.isChecked) {
            list.add(binding.surface1.surface)
        }
//        if (binding.swImageReader1.isChecked) {
//            list.add(imageReader1.surface)
//        }
//        if (binding.swImageReader2.isChecked) {
//            list.add(imageReader2.surface)
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
    private fun computeSizes() {
        var viewSize = when (binding.root.display.rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                binding.surfaceMain.run { Size(width, height) }
            }
            else -> {
                binding.surfaceMain.run { Size(height, width) }
            }
        }
        var viewRational = viewSize.toRational()

        // 几种标准分辨率
        var standardRatios = arrayListOf(
            Rational(21, 9),
            Rational(2, 1),
            Rational(16, 9),
            Rational(4, 3),
            Rational(1, 1)
        )

        val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

        var imageFormatSizes = config.getOutputSizes(imageReaderFormat)
//        for (size in imageFormatSizes) {
//            Log.d(TAG, "size: $size, ratio: ${size.toRational()}")
//        }

        val allPreviewSizes = config.getOutputSizes(SurfaceHolder::class.java).let {
            if (it == null || it.isEmpty()) {
                throw IllegalStateException("No preview sizes")
            }
            it
        }
        val allAnalysisSizes = config.getOutputSizes(ImageReader::class.java).let {
            if (it == null || it.isEmpty()) {
                throw IllegalStateException("No analysis sizes")
            }
            it
        }

        // 将分辨率按照宽高比分组
        val previewRationalSizeMap = groupSizeByRatio(allPreviewSizes)
        val analysisRationalSizeMap = groupSizeByRatio(allAnalysisSizes)

        // 按宽高比与view最接近来排序，这样可以避免无用的大尺寸图像
        standardRatios.sortBy {
            abs(it.toFloat() - viewRational.toFloat())
        }

        for (rational in standardRatios) {
            val previewSizes = previewRationalSizeMap[rational] ?: continue
            val analysisSizes = analysisRationalSizeMap[rational] ?: continue

            var pixelDiff = Int.MAX_VALUE
            var finalPreviewSize: Size? = null
            for (size in previewSizes) {
                if (abs(size.area() - viewSize.area()) < pixelDiff) {
                    pixelDiff = abs(size.area() - viewSize.area())
                    finalPreviewSize = size
                }
            }
            previewSize = finalPreviewSize!!

            analysisSizes.sortBy { it.area() }
            imageReaderSize = analysisSizes[0]

            break
        }
        previewSize = Size(1280, 720)
        Timber.d("previewViewSize: $viewSize, ratio: $viewRational")
        Timber.d("previewSize: $previewSize, ratio: ${previewSize.toRational()}")
        Timber.d("analysisSize: $imageReaderSize, ratio: ${imageReaderSize.toRational()}")

    }

    private fun groupSizeByRatio(sizes: Array<Size>): Map<Rational, ArrayList<Size>> {
        var result = HashMap<Rational, ArrayList<Size>>()
        for (size in sizes) {
            val rational = size.toRational()
            if (result[rational] == null) {
                result[rational] = ArrayList()
            }

            result[rational]?.add(size)
        }
        return result
    }




    private fun selectBackCameraID(): String {
        queryCameraInfo()
        cameraInfoMap.values.forEach {
            if (it.lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                return@forEach
            }

            if (!it.isLogical) {
                return@forEach
            }

            // Query the available capabilities and output formats
            val capabilities = it.characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
            val outputFormats = it.characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.outputFormats


            // Return cameras that support RAW capability
            if (capabilities.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) &&
                outputFormats.contains(ImageFormat.RAW_SENSOR)) {
                return it.cameraID
            }

        }
        throw RuntimeException("no suitable back camera id found")
    }

    private fun queryCameraInfo() {
        val presentIdQueue = ArrayDeque<String>().apply {
            addAll(cameraManager.cameraIdList)
        }
        cameraInfoMap.clear()

        val logicalIdQueue = ArrayDeque<String>()

        // 先处理通过CameraManager能查询到的
        while (presentIdQueue.isNotEmpty()) {
            val id = presentIdQueue.poll()
            if (cameraInfoMap.containsKey(id)) {
                continue
            }
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val infoWrapper = CameraInfoWrapper(id, characteristics).apply {
                isPresentByCameraManager = true
            }
            cameraInfoMap.put(id, infoWrapper)
            if (infoWrapper.isLogical) {
                logicalIdQueue.add(id)
            }
        }

        // 然后处理隐藏的物理镜头。如果一个摄像头既能被CameraManager独立查询到，又属于逻辑镜头。
        // 那最终将它视作属于逻辑镜头，要打开它，就通过逻辑镜头打开
        while (logicalIdQueue.isNotEmpty()) {
            val logicalID = logicalIdQueue.poll()
            val logicalInfo = cameraInfoMap[logicalID] ?: continue
            for (physicalID in logicalInfo.logicalPhysicalIDs) {
                val characteristics = cameraManager.getCameraCharacteristics(physicalID)
                val infoWrapper = CameraInfoWrapper(physicalID, characteristics).apply {
                    isPresentByCameraManager = false
                    this.logicalID = logicalID
                }
                cameraInfoMap[physicalID] = infoWrapper
            }
        }

        val infoList = ArrayList<CameraInfoWrapper>().apply {
            addAll(cameraInfoMap.values)
        }


        infoList.sortWith(Comparator { o1, o2 ->
            if (o1.lensFacing == CameraCharacteristics.LENS_FACING_FRONT && o2.lensFacing != CameraCharacteristics.LENS_FACING_FRONT) {
                -1
            } else if (o1.lensFacing != CameraCharacteristics.LENS_FACING_FRONT && o2.lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                1
            } else {
                if (o1.isLogical && !o2.isLogical) {
                    -1
                } else if (!o1.isLogical && o2.isLogical) {
                    1
                } else {
                    if (o1.focalArray[0] > o2.focalArray[0]) {
                        1
                    } else if (o1.focalArray[0] < o2.focalArray[0]) {
                        -1
                    } else {
                        0
                    }
                }
            }
        })

        infoList.forEach {
            Timber.d(it.toString())
        }

        infoList.forEach {
            val str = """
                {
                    id: ${it.cameraID}
                    supportLevel: ${it.characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}
                    maxOutputProc: ${it.characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC)}
                    maxOutputStalling: ${it.characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING)}
                    maxOutputRaw: ${it.characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)}
                    maxInputStream: ${it.characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS)}
                }
            """.trimIndent()
            Timber.d(str)
        }



    }




}