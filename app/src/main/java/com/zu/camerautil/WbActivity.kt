package com.zu.camerautil

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.SeekBar
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.BaseCameraLogic
import com.zu.camerautil.camera.getWbModeName
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ActivityWbBinding
import com.zu.camerautil.preview.Camera2PreviewView
import com.zu.camerautil.preview.PreviewViewImplementation
import com.zu.camerautil.view.CameraSpinnerAdapter
import timber.log.Timber
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class WbActivity : AppCompatActivity() {

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

    private lateinit var binding: ActivityWbBinding
    private lateinit var wbModeAdapter: ArrayAdapter<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWbBinding.inflate(layoutInflater)
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
                return arrayListOf(binding.surfaceMain.surface)
            }

            override fun getCaptureSurfaceList(): List<Surface> {
                return getSessionSurfaceList()
            }

            override fun configBuilder(requestBuilder: CaptureRequest.Builder) {
                if (binding.spWbMode.selectedItemPosition >= 0) {
                    cameraLogic.currentCameraInfo?.awbModes?.get(binding.spWbMode.selectedItemPosition)?.let {
                        requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, it)
                    }
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

        cameraLogic.sessionStateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                currentFps = binding.cameraSelector.currentFps
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {

            }
        }
    }

    private fun initViews() {
        binding.surfaceMain.implementationType = Camera2PreviewView.ImplementationType.TEXTURE_VIEW
        binding.surfaceMain.scaleType = Camera2PreviewView.ScaleType.FIT_CENTER
        binding.surfaceMain.surfaceStateListener = surfaceStateListener

        binding.cameraSelector.onConfigChangedListener = {camera, fps, size ->
            if (camera.cameraID != openedCameraID) {
                updateWbModes(camera)
            }
//            if (currentSize !=  binding.cameraSelector.currentSize) {
//                currentSize = binding.cameraSelector.currentSize
//                binding.surfaceMain.previewSize = currentSize!!
//            }
            currentSize = binding.cameraSelector.currentSize
            binding.surfaceMain.previewSize = currentSize!!
            if (camera.cameraID != openedCameraID || size != currentSize || fps != currentFps) {
                Timber.d("onConfigChanged: camera: ${camera.cameraID}, size: $size, fps: $fps")
                // 注意这里回调本来就是在主线程内，但是为什么还要post呢？
                // 因为上面PreviewView设置分辨率后，会调用一次requestLayout或者postInvalidate进行重新布局。
                // 但是重新布局并不会立刻进行而是排进主线程队列里。如果这里直接打开相机，就会导致相机输出时PreviewView
                // 并没有被设置为正确的布局，所以这里把打开相机也post到主线程的队列里并且保证它在重布局PreviewView
                // 的后面
                binding.root.post {
                    cameraLogic.closeDevice()
                    cameraLogic.openDevice(camera)
                }
//                binding.root.postDelayed({
//                    cameraLogic.closeDevice()
//                    cameraLogic.openDevice(camera)
//                }, 2000)
            }
        }

        wbModeAdapter = ArrayAdapter(this, R.layout.item_camera_simple,R.id.tv)
        binding.spWbMode.adapter = wbModeAdapter
        binding.spWbMode.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val mode = cameraLogic.currentCameraInfo?.awbModes?.get(position) ?: return
                val isManual = mode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
                binding.tvInputWb.visibility = if (isManual) View.VISIBLE else View.VISIBLE
                binding.sbKelvin.isEnabled = isManual
                binding.sbGreenRed.isEnabled = isManual
                cameraLogic.updateCaptureRequestParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }

    }

    private fun updateWbModes(cameraInfo: CameraInfoWrapper) {
        val nameList = ArrayList<String>()
        cameraInfo.awbModes?.forEach {
            nameList.add(getWbModeName(it) ?: "$it")
        }
        wbModeAdapter.clear()
        wbModeAdapter.addAll(nameList)
    }
}