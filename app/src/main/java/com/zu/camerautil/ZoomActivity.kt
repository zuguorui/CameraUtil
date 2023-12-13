package com.zu.camerautil

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.computePreviewSize
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityZoomBinding
import com.zu.camerautil.view.CameraAdapter
import timber.log.Timber
import java.util.concurrent.Executors

class ZoomActivity : AppCompatActivity() {

    private val cameraManager: CameraManager by lazy {
        ((getSystemService(Context.CAMERA_SERVICE)) as CameraManager)
    }

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private var cameraID: String? = null
        set(value) {
            field = value

        }
    private val characteristics: CameraCharacteristics
        get() = cameraInfoMap[cameraID]!!.characteristics

    private var camera: CameraDevice? = null

    private var session: CameraCaptureSession? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private lateinit var previewSize: Size

    private var cameraThread = HandlerThread("CameraThread").apply { start() }

    private var cameraHandler = Handler(cameraThread.looper)

    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            // 获取最接近容器宽高比的Camera输出，并且设置给SurfaceView
            val viewSize = with(binding.surfaceMain) {
                Size(width, height)
            }
            previewSize = computePreviewSize(characteristics, viewSize, binding.root.display.rotation, SurfaceHolder::class.java)
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

    private lateinit var binding: ActivityZoomBinding
    private lateinit var adapter: CameraAdapter
    private val cameraList: ArrayList<CameraInfoWrapper> by lazy {
        ArrayList<CameraInfoWrapper>().apply {
            addAll(cameraInfoMap.values)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityZoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
    }

    private fun initViews() {
        cameraID = selectCameraID(cameraInfoMap, CameraCharacteristics.LENS_FACING_BACK, true)
        adapter = CameraAdapter()
        adapter.setData(cameraList)
        binding.spinnerCamera.adapter = adapter
        binding.spinnerCamera.setSelection(cameraList.indexOf(cameraInfoMap[cameraID]))
        binding.spinnerCamera.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {

            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }
    }
}