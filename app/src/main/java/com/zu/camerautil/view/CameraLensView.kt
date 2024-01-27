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
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.databinding.ItemCameraParamBinding
import timber.log.Timber

class CameraLensView: AbsCameraParamView {

    private lateinit var lensBinding: ItemCameraParamBinding
    private lateinit var sizeBinding: ItemCameraParamBinding
    private lateinit var fpsBinding: ItemCameraParamBinding

    private lateinit var lensPopupWindow: SelectionParamPopupWindow
    private lateinit var sizePopupWindow: SelectionParamPopupWindow
    private lateinit var fpsPopupWindow: SelectionParamPopupWindow

    private val layoutInflater = LayoutInflater.from(context)

    private val cameraMap = HashMap<String, CameraInfoWrapper>()

    private val sizeFpsMap = HashMap<Size, List<FPS>>()

    private val cameraList = ArrayList<CameraInfoWrapper>()

    private var configChanged = false
    lateinit var currentCamera: CameraInfoWrapper
        private set

    private val fpsList = ArrayList<FPS>()
    lateinit var currentFps: FPS
        private set

    private val sizeList = ArrayList<Size>()
    lateinit var currentSize: Size
        private set

    var onConfigChangedListener: ((cameraInfoWrapper: CameraInfoWrapper, fps: FPS, size: Size) -> Unit)? = null
    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        initViews()
    }

    private fun initViews() {
        lensBinding = ItemCameraParamBinding.inflate(layoutInflater, this, false)
        lensBinding.run {
            tvName.text = "镜头"
            tvValue.text = ""
            tvMode.visibility = GONE
            root.isClickable = true
            root.setOnClickListener {
                showLensMenu()
            }
        }

        sizeBinding = ItemCameraParamBinding.inflate(layoutInflater, this, false)
        sizeBinding.run {
            tvName.text = "分辨率"
            tvValue.text = ""
            tvMode.visibility = GONE
            root.isClickable = true
            root.setOnClickListener {
                showSizeMenu()
            }
        }

        fpsBinding = ItemCameraParamBinding.inflate(layoutInflater, this, false)
        fpsBinding.run {
            tvName.text = "FPS"
            tvValue.text = ""
            //tvMode.visibility = GONE
            root.isClickable = true
            root.setOnClickListener {
                showFpsMenu()
            }
        }

        val itemViews = arrayListOf(lensBinding.root, sizeBinding.root, fpsBinding.root)

        setItems(itemViews)

        lensPopupWindow = SelectionParamPopupWindow(context).apply {
            onItemClickListener = { param ->
                this.dismiss()
                val camera = cameraList[param.id]
                updateCamera(camera)
            }
        }

        sizePopupWindow = SelectionParamPopupWindow(context).apply {
            onItemClickListener = { param ->
                this.dismiss()
                val size = sizeList[param.id]
                updateSize(size)
            }
        }

        fpsPopupWindow = SelectionParamPopupWindow(context).apply {
            onItemClickListener = { param ->
                this.dismiss()
                val fps = fpsList[param.id]
                updateFps(fps)
            }
        }

    }

    private fun showLensMenu() {
        lensPopupWindow.showAsDropDown(lensBinding.root)
    }

    private fun showSizeMenu() {
        sizePopupWindow.showAsDropDown(sizeBinding.root)
    }

    private fun showFpsMenu() {
        fpsPopupWindow.showAsDropDown(fpsBinding.root)
    }

    fun setCameras(cameras: Collection<CameraInfoWrapper>) {
        cameraMap.clear()
        cameraList.clear()
        for (camera in cameras) {
            cameraMap[camera.cameraID] = camera
        }
        cameraList.addAll(cameraMap.values)

        val cameraParams = ArrayList<SelectionParamPopupWindow.Param>(cameraList.size)

        for (i in cameraList.indices) {
            val camera = cameraList[i]
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
        if (this@CameraLensView::currentCamera.isInitialized && camera.cameraID == currentCamera.cameraID) {
            return
        }
        configChanged = true
        currentCamera = camera
        lensBinding.tvValue.text = camera.cameraID
        updateSizeByCamera()
    }

    private fun updateSize(size: Size) {
        if (this@CameraLensView::currentSize.isInitialized && size == currentSize) {
            if (configChanged) {
                notifyConfigChanged()
            }
            return
        }
        configChanged = true
        currentSize = size
        sizeBinding.tvValue.text = with(currentSize) {
            "${this.width}x${this.height}"
        }
        updateFpsBySize()
    }

    private fun updateFps(fps: FPS) {
        if (this@CameraLensView::currentFps.isInitialized && fps == currentFps) {
            if (configChanged) {
                notifyConfigChanged()
            }
            return
        }
        configChanged = true
        currentFps = fps
        fpsBinding.tvValue.text = "${currentFps.value}"
        fpsBinding.tvMode.text = if (currentFps.type == FPS.Type.NORMAL) "N" else "H"

        notifyConfigChanged()
    }

    private fun updateSizeByCamera() {
        sizeList.clear()
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

        sizeList.addAll(sizeFpsMap.keys)
        sizeList.sortWith { o1, o2 ->
            if (o1.width != o2.width) {
                o1.width - o2.width
            } else {
                o1.height - o2.height
            }
        }

        val sizeParams = ArrayList<SelectionParamPopupWindow.Param>()
        for (i in sizeList.indices) {
            val size = sizeList[i]
            val str = size.run {
                "${width}x${height}"
            }
            val param = SelectionParamPopupWindow.Param(str, i)
            sizeParams.add(param)
        }
        sizePopupWindow.setData(sizeParams)

        var size: Size = if (this::currentSize.isInitialized) {
            if (!sizeFpsMap.contains(currentSize)) {
                sizeList[0]
            } else {
                currentSize
            }
        } else {
            sizeList[0]
        }
        updateSize(size)
    }

    private fun updateFpsBySize() {
        fpsList.clear()
        sizeFpsMap[currentSize]?.let {
            fpsList.addAll(it)
        }

        val fpsParams = ArrayList<SelectionParamPopupWindow.Param>()
        for (i in fpsList.indices) {
            val fps = fpsList[i]
            val param = SelectionParamPopupWindow.Param(fps.toString(), i)
            fpsParams.add(param)
        }
        fpsPopupWindow.setData(fpsParams)

        var fps: FPS = if (this::currentFps.isInitialized) {
            if (!fpsList.contains(currentFps)) {
                fpsList.first()
            } else {
                currentFps
            }
        } else {
            fpsList.first()
        }
        updateFps(fps)
    }


    private fun notifyConfigChanged() {
        configChanged = false
        onConfigChangedListener?.invoke(currentCamera, currentFps, currentSize)
    }

    fun setCamera(camera: CameraInfoWrapper) {
        if (!cameraList.contains(camera)) {
            return
        }
        updateCamera(camera)
    }

    fun setSize(size: Size) {
        if (!sizeList.contains(size)) {
            return
        }
        updateSize(size)
    }

    fun setFps(fps: FPS) {
        if (!fpsList.contains(fps)) {
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