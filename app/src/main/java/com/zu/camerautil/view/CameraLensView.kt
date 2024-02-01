package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.bean.FpsParam
import com.zu.camerautil.bean.IDisplayParam
import com.zu.camerautil.bean.LensParam
import com.zu.camerautil.bean.SizeParam
import com.zu.camerautil.camera.selectCameraID
import timber.log.Timber

class CameraLensView: AbsCameraParamView {

    private lateinit var lensView: ParamView
    private lateinit var sizeView: ParamView
    private lateinit var fpsView: ParamView

    private lateinit var lensPopupWindow: SelectionParamPopupWindow<CameraInfoWrapper>
    private lateinit var sizePopupWindow: SelectionParamPopupWindow<Size>
    private lateinit var fpsPopupWindow: SelectionParamPopupWindow<FPS>

    private var lensParam = LensParam()
    private var sizeParam = SizeParam()
    private var fpsParam = FpsParam()

    private val cameraMap = HashMap<String, CameraInfoWrapper>()

    private val sizeFpsMap = HashMap<Size, List<FPS>>()

    private var configChanged = false
    var currentCamera: CameraInfoWrapper?
        get() = lensParam.value
        private set(value) {
            lensParam.value = value
            lensView.notifyDataChanged()
        }

    var currentFps: FPS?
        get() = fpsParam.value
        private set(value) {
            fpsParam.value = value
            fpsView.notifyDataChanged()
        }

    var currentSize: Size?
        get() = sizeParam.value
        private set(value) {
            sizeParam.value = value
            sizeView.notifyDataChanged()
        }

    var onConfigChangedListener: ((cameraInfoWrapper: CameraInfoWrapper, fps: FPS, size: Size) -> Unit)? = null
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        initViews()
        if (isInEditMode) {
            lensView.param = object : IDisplayParam {
                override val name: String
                    get() = "Lens"
                override val isModal: Boolean
                    get() = false
                override val currentValue: String
                    get() = "0"
                override val currentMode: String
                    get() = throw UnsupportedOperationException("unsupported")
            }

            sizeView.param = object : IDisplayParam {
                override val name: String
                    get() = "Size"
                override val isModal: Boolean
                    get() = false
                override val currentValue: String
                    get() = "1920x1080"
                override val currentMode: String
                    get() = throw UnsupportedOperationException("unsupported")
            }

            fpsView.param = object : IDisplayParam {
                override val name: String
                    get() = "FPS"
                override val isModal: Boolean
                    get() = true
                override val currentValue: String
                    get() = "60FPS"
                override val currentMode: String
                    get() = "H"
            }
        }
    }

    private fun initViews() {
        lensView = ParamView(context).apply {
            param = lensParam
            setOnClickListener {
                showLensMenu()
            }
        }

        sizeView = ParamView(context).apply {
            param = sizeParam
            setOnClickListener {
                showSizeMenu()
            }
        }

        fpsView = ParamView(context).apply {
            param = fpsParam
            setOnClickListener {
                showFpsMenu()
            }
        }

        val itemViews = arrayListOf(lensView, sizeView, fpsView)

        setItems(itemViews)

        lensPopupWindow = SelectionParamPopupWindow<CameraInfoWrapper>(context).apply {
            param = lensParam
            onParamSelectedListener = { camera ->
                updateCamera(camera)
            }
        }

        sizePopupWindow = SelectionParamPopupWindow<Size>(context).apply {
            param = sizeParam
            onParamSelectedListener = { size ->
                updateSize(size)
            }
        }

        fpsPopupWindow = SelectionParamPopupWindow<FPS>(context).apply {
            param = fpsParam
            onParamSelectedListener = { fps ->
                updateFps(fps)
            }
        }

    }

    private fun showLensMenu() {
        lensPopupWindow.show(lensView, paramPanelPopupGravity)
    }

    private fun showSizeMenu() {
        sizePopupWindow.show(sizeView, paramPanelPopupGravity)
    }

    private fun showFpsMenu() {
        Timber.d("showFpsMenu")
        fpsPopupWindow.show(fpsView, paramPanelPopupGravity)
    }

    fun setCameras(cameras: Collection<CameraInfoWrapper>) {
        cameraMap.clear()
        lensParam.values.clear()
        for (camera in cameras) {
            cameraMap[camera.cameraID] = camera
        }
        lensParam.values.addAll(cameraMap.values)
        lensPopupWindow.notifyDataChanged()
        val cameraID = selectCameraID(cameraMap, CameraCharacteristics.LENS_FACING_BACK, true)
        updateCamera(cameraMap[cameraID]!!)
    }

    private fun updateCamera(camera: CameraInfoWrapper) {
        if (camera == currentCamera) {
            return
        }
        configChanged = true
        currentCamera = camera
        updateSizeByCamera()
    }

    private fun updateSize(size: Size) {
        if (size == currentSize) {
            if (configChanged) {
                notifyConfigChanged()
            }
            return
        }
        configChanged = true
        currentSize = size
        updateFpsBySize()
    }

    private fun updateFps(fps: FPS) {
        if (fps == currentFps) {
            if (configChanged) {
                notifyConfigChanged()
            }
            return
        }
        configChanged = true
        currentFps = fps
        notifyConfigChanged()
    }

    private fun updateSizeByCamera() {
        val currentCamera = currentCamera ?: return
        sizeParam.values.clear()
        sizeFpsMap.clear()

        val normalFpsSet = HashSet<FPS>()
        for (fpsRange in currentCamera.fpsRanges) {
            if (fpsRange.lower == fpsRange.upper) {
                normalFpsSet.add(FPS(fpsRange.lower, FPS.Type.NORMAL))
            }
        }

        val normalSizeSet = HashSet<Size>().apply {
            currentCamera.formatSizeMap[ImageFormat.PRIVATE]?.forEach {
                if (STANDARD_SIZE_SET.contains(it)) {
                    add(it)
                }
            }
        }

        val highSpeedSizeSet = HashSet<Size>().apply {
            currentCamera.highSpeedSizeFpsMap.keys.forEach {
                if (STANDARD_SIZE_SET.contains(it)) {
                    add(it)
                }
            }
        }

        val sizeSet = HashSet<Size>().apply {
            addAll(normalSizeSet)
            addAll(highSpeedSizeSet)
        }

        for (size in sizeSet) {
            val fpsSet = HashSet<FPS>()
            if (normalSizeSet.contains(size)) {
                fpsSet.addAll(normalFpsSet)
            }
            if (highSpeedSizeSet.contains(size)) {
                currentCamera.highSpeedSizeFpsMap[size]?.forEach {
                    if (it.lower == it.upper) {
                        fpsSet.add(FPS(it.lower, FPS.Type.HIGH_SPEED))
                    }
                }
            }
            if (fpsSet.isNotEmpty()) {
                sizeFpsMap[size] = fpsSet.toMutableList().apply {
                    sortWith { o1, o2 ->
                        if (o1.type != o2.type) {
                            if (o1.type == FPS.Type.NORMAL) {
                                -1
                            } else {
                                1
                            }
                        } else {
                            o1.value - o2.value
                        }
                    }
                }
            } else {
                Timber.e("No FPS found for size $size")
            }
        }

        sizeParam.values.addAll(sizeFpsMap.keys)
        sizeParam.values.sortWith { o1, o2 ->
            if (o1.width != o2.width) {
                o1.width - o2.width
            } else {
                o1.height - o2.height
            }
        }

        sizePopupWindow.notifyDataChanged()

        val size = currentSize?.let {
            if (sizeFpsMap.contains(currentSize)) {
                it
            } else {
                sizeParam.values[0]
            }
        } ?: sizeParam.values[0]

        updateSize(size)
    }

    private fun updateFpsBySize() {
        fpsParam.values.clear()
        sizeFpsMap[currentSize]?.let {
            fpsParam.values.addAll(it)
        }
        fpsPopupWindow.notifyDataChanged()

        val fps = currentFps?.let {
            if (fpsParam.values.contains(it)) {
                it
            } else {
                fpsParam.values[0]
            }
        } ?: fpsParam.values[0]

        updateFps(fps)
    }


    private fun notifyConfigChanged() {
        configChanged = false
        onConfigChangedListener?.invoke(currentCamera!!, currentFps!!, currentSize!!)
    }

    fun setCamera(camera: CameraInfoWrapper) {
        if (!lensParam.values.contains(camera)) {
            return
        }
        updateCamera(camera)
    }

    fun setSize(size: Size) {
        if (!sizeParam.values.contains(size)) {
            return
        }
        updateSize(size)
    }

    fun setFps(fps: FPS) {
        if (!fpsParam.values.contains(fps)) {
            return
        }
        updateFps(fps)
    }

    fun setEnable(enable: Boolean) {
        lensView.isEnabled = enable
        sizeView.isEnabled = enable
        fpsView.isEnabled = enable
    }

    companion object {

        private val STANDARD_SIZE_SET = HashSet<Size>().apply {
            add(Size(1280, 720))
            add(Size(1920, 1080))
            add(Size(2560, 1440))
            add(Size(3840, 2160))
        }
    }

}