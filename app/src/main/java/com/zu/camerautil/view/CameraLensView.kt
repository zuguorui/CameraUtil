package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.bean.FpsParam
import com.zu.camerautil.bean.LensParam
import com.zu.camerautil.bean.SelectionParam
import com.zu.camerautil.bean.SizeParam
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ItemCameraParamBinding
import timber.log.Timber

class CameraLensView: AbsCameraParamView {

    private lateinit var lensView: ParamView
    private lateinit var sizeView: ParamView
    private lateinit var fpsView: ParamView

    private lateinit var lensPopupWindow: SelectionParamPopupWindow<CameraInfoWrapper>
    private lateinit var sizePopupWindow: SelectionParamPopupWindow<Size>
    private lateinit var fpsPopupWindow: SelectionParamPopupWindow<FPS>

    private var lensParam = LensParam
    private var sizeParam = SizeParam
    private var fpsParam = FpsParam

    private val layoutInflater = LayoutInflater.from(context)

    private val cameraMap = HashMap<String, CameraInfoWrapper>()

    private val sizeFpsMap = HashMap<Size, List<FPS>>()

    private var configChanged = false
    var currentCamera: CameraInfoWrapper?
        get() = lensParam.current
        private set(value) {
            lensParam.current = value
        }

    var currentFps: FPS?
        get() = fpsParam.current
        private set(value) {
            fpsParam.current = value
        }

    var currentSize: Size?
        get() = sizeParam.current
        private set(value) {
            sizeParam.current = value
        }

    var onConfigChangedListener: ((cameraInfoWrapper: CameraInfoWrapper, fps: FPS, size: Size) -> Unit)? = null
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        initViews()
    }

    private fun initViews() {
        lensView = ParamView(context)

        sizeView = ParamView(context)

        fpsView = ParamView(context)

        val itemViews = arrayListOf(lensView, sizeView, fpsView)

        setItems(itemViews)

        lensPopupWindow = SelectionParamPopupWindow<CameraInfoWrapper>(context).apply {
            onItemClickListener = { param ->
                this.dismiss()
                val camera = lensParam.values[param.id]
                updateCamera(camera)
            }
        }

        sizePopupWindow = SelectionParamPopupWindow(context).apply {
            onItemClickListener = { param ->
                this.dismiss()
                val size = sizeParam.values[param.id]
                updateSize(size)
            }
        }

        fpsPopupWindow = SelectionParamPopupWindow(context).apply {
            onItemClickListener = { param ->
                this.dismiss()
                val fps = fpsParam.values[param.id]
                updateFps(fps)
            }
        }

    }

    private fun showLensMenu() {
        lensPopupWindow.show(lensBinding.root, paramPanelPopupGravity)
    }

    private fun showSizeMenu() {
        sizePopupWindow.show(sizeBinding.root, paramPanelPopupGravity)
    }

    private fun showFpsMenu() {
        fpsPopupWindow.show(fpsBinding.root, paramPanelPopupGravity)
    }

    fun setCameras(cameras: Collection<CameraInfoWrapper>) {
        cameraMap.clear()
        lensParam.values.clear()
        for (camera in cameras) {
            cameraMap[camera.cameraID] = camera
        }
        lensParam.values.addAll(cameraMap.values)

        val cameraParams = ArrayList<SelectionParamPopupWindow.Param>(lensParam.values.size)

        for (i in lensParam.values.indices) {
            val camera = lensParam.values[i]
            val facing = if (camera.lensFacing == CameraCharacteristics.LENS_FACING_BACK) "back" else "front"
            val logical = if (camera.isLogical) "logical" else "physical"
            val focal = if (camera.focalArray.isNotEmpty()) String.format("%.1f", camera.focalArray[0]) else "_"
            val str = "Camera${camera.cameraID}_${facing}_${logical}_focal(${focal})"

            val color = if (camera.isInCameraIdList) Color.GREEN else Color.RED

            val param = SelectionParamPopupWindow.Param(str, i, color)
            cameraParams.add(param)
        }
        lensPopupWindow.setData(cameraParams)

        val cameraID = selectCameraID(cameraMap, CameraCharacteristics.LENS_FACING_BACK, true)
        updateCamera(cameraMap[cameraID]!!)
    }

    private fun updateCamera(camera: CameraInfoWrapper) {
        if (camera == currentCamera) {
            return
        }
        configChanged = true
        currentCamera = camera
        lensBinding.tvValue.text = camera.cameraID
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
        sizeBinding.tvValue.text = with(currentSize!!) {
            "${this.width}x${this.height}"
        }
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
        fpsBinding.tvValue.text = "${currentFps?.value}"
        fpsBinding.tvMode.text = if (currentFps?.type == FPS.Type.NORMAL) "N" else "H"

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

        val sizeParams = ArrayList<SelectionParamPopupWindow.Param>()
        for (i in sizeParam.values.indices) {
            val size = sizeParam.values[i]
            val str = size.run {
                "${width}x${height}"
            }
            val param = SelectionParamPopupWindow.Param(str, i)
            sizeParams.add(param)
        }
        sizePopupWindow.setData(sizeParams)

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

        val fpsParams = ArrayList<SelectionParamPopupWindow.Param>()
        for (i in fpsParam.values.indices) {
            val fps = fpsParam.values[i]
            val param = SelectionParamPopupWindow.Param(fps.toString(), i)
            fpsParams.add(param)
        }
        fpsPopupWindow.setData(fpsParams)

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
        lensBinding.root.isEnabled = enable
        sizeBinding.root.isEnabled = enable
        fpsBinding.root.isEnabled = enable
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