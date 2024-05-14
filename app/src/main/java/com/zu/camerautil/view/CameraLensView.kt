package com.zu.camerautil.view

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Size
import com.zu.camerautil.bean.AbsCameraParam
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.bean.FpsParam
import com.zu.camerautil.bean.LensParam
import com.zu.camerautil.bean.SelectionParam
import com.zu.camerautil.bean.ValueListener
import com.zu.camerautil.bean.SizeParam
import com.zu.camerautil.camera.selectCameraID
import timber.log.Timber

class CameraLensView: AbsCameraParamView {

    private lateinit var lensView: ParamView
    private lateinit var sizeView: ParamView
    private lateinit var fpsView: ParamView

    private lateinit var lensPopupWindow: SelectionParamPopupWindow
    private lateinit var sizePopupWindow: SelectionParamPopupWindow
    private lateinit var fpsPopupWindow: SelectionParamPopupWindow

    private val lensListener: ValueListener<CameraInfoWrapper> = { camera ->
        camera?.let {
            configChanged = true
            updateSizeByCamera()
        }
    }

    private val sizeListener: ValueListener<Size> = { size ->
        size?.let {
            configChanged = true
            updateFpsBySize()
        }
    }

    private val fpsListener: ValueListener<FPS> = { fps ->
        fps?.let {
            configChanged = true
            notifyConfigChanged()
        }
    }

    private var lensParam = LensParam().apply {
        addValueListener(lensListener)
    }
    private var sizeParam = SizeParam().apply {
        addValueListener(sizeListener)
    }
    private var fpsParam = FpsParam().apply {
        addValueListener(fpsListener)
    }

    private val cameraMap = HashMap<String, CameraInfoWrapper>()

    private val sizeFpsMap = HashMap<Size, List<FPS>>()

    private var configChanged = false
    var currentCamera: CameraInfoWrapper?
        get() = lensParam.value
        private set(value) {
            lensParam.value = value
        }

    var currentFps: FPS?
        get() = fpsParam.value
        private set(value) {
            fpsParam.value = value
        }

    var currentSize: Size?
        get() = sizeParam.value
        private set(value) {
            sizeParam.value = value
        }

    var onConfigChangedListener: ((cameraInfoWrapper: CameraInfoWrapper, fps: FPS, size: Size) -> Unit)? = null
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        initViews()
        if (isInEditMode) {

            lensView.param = object : AbsCameraParam<CameraInfoWrapper>() {
                override val name: String
                    get() = "Lens"
                override val isModal: Boolean
                    get() = false
                override val valueName: String
                    get() = "0"
                override val modeName: String
                    get() = throw UnsupportedOperationException("unsupported")
            } as AbsCameraParam<Any>

            sizeView.param = object : AbsCameraParam<Size>() {
                override val name: String
                    get() = "Size"
                override val isModal: Boolean
                    get() = false
                override val valueName: String
                    get() = "1920x1080"
                override val modeName: String
                    get() = throw UnsupportedOperationException("unsupported")

            } as AbsCameraParam<Any>

            fpsView.param = object : AbsCameraParam<FPS>() {
                override val name: String
                    get() = "FPS"
                override val isModal: Boolean
                    get() = true
                override val valueName: String
                    get() = "60FPS"
                override val modeName: String
                    get() = "H"

            } as AbsCameraParam<Any>
        }
    }

    private fun initViews() {
        lensView = ParamView(context).apply {
            param = lensParam as AbsCameraParam<Any>
            setOnClickListener {
                showLensMenu()
            }
        }

        sizeView = ParamView(context).apply {
            param = sizeParam as AbsCameraParam<Any>
            setOnClickListener {
                showSizeMenu()
            }
        }

        fpsView = ParamView(context).apply {
            param = fpsParam as AbsCameraParam<Any>
            setOnClickListener {
                showFpsMenu()
            }
        }

        val itemViews = arrayListOf(lensView, sizeView, fpsView)

        setItems(itemViews)

        lensPopupWindow = SelectionParamPopupWindow(context, CameraParamID.LENS).apply {
            param = lensParam as SelectionParam<Any>
        }

        sizePopupWindow = SelectionParamPopupWindow(context, CameraParamID.SIZE).apply {
            param = sizeParam as SelectionParam<Any>
        }

        fpsPopupWindow = SelectionParamPopupWindow(context, CameraParamID.FPS).apply {
            param = fpsParam as SelectionParam<Any>
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
        val cameraID = selectCameraID(cameraMap, CameraCharacteristics.LENS_FACING_BACK, true)
        configChanged = true
        currentCamera = cameraMap[cameraID]
    }

    private fun updateSizeByCamera() {
        val currentCamera = currentCamera ?: return
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

        val list = ArrayList<Size>().apply {
            addAll(sizeFpsMap.keys)
        }

        list.sortWith { o1, o2 ->
            if (o1.width != o2.width) {
                o1.width - o2.width
            } else {
                o1.height - o2.height
            }
        }

        sizeParam.values.clear()
        sizeParam.values.addAll(list)

        val size = currentSize?.let {
            if (sizeFpsMap.contains(currentSize)) {
                it
            } else {
                sizeParam.values[0]
            }
        } ?: sizeParam.values[0]

        if (size == currentSize) {
            // 如果fps是空或者当前镜头当前size没有之前的fps，都要更新一下。
            // 但是由于size和之前一样，所以不能依赖view回调去更新fps，只能手动更新
            currentFps?.let {
                if (sizeFpsMap[size]?.contains(it) == true) {
                    if (configChanged) {
                        notifyConfigChanged()
                    }
                } else {
                    updateFpsBySize()
                }
            } ?: kotlin.run {
                updateFpsBySize()
            }
        } else {
            configChanged = true
            currentSize = size
        }
    }

    private fun updateFpsBySize() {
        fpsParam.values.clear()
        sizeFpsMap[currentSize]?.let {
            fpsParam.values.addAll(it)
        }

        val fps = currentFps?.let {
            if (fpsParam.values.contains(it)) {
                it
            } else {
                fpsParam.values[0]
            }
        } ?: fpsParam.values[0]

        if (fps == currentFps) {
            if (configChanged) {
                notifyConfigChanged()
            }
        } else {
            configChanged = true
            currentFps = fps
        }
    }


    private fun notifyConfigChanged() {
        Timber.d("notifyConfigChanged, lens = $currentCamera, size = $currentSize, fps = $currentFps")
        configChanged = false
        onConfigChangedListener?.invoke(currentCamera!!, currentFps!!, currentSize!!)
    }

    fun setCamera(camera: CameraInfoWrapper) {
        if (!lensParam.values.contains(camera)) {
            return
        }
        currentCamera = camera
    }

    fun setSize(size: Size) {
        if (!sizeParam.values.contains(size)) {
            return
        }
        currentSize = size
    }

    fun setFps(fps: FPS) {
        if (!fpsParam.values.contains(fps)) {
            return
        }
        currentFps = fps
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