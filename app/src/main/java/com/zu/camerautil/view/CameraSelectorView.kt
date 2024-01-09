package com.zu.camerautil.view

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Spinner
import com.zu.camerautil.R
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.camera.selectCameraID
import com.zu.camerautil.util.dpToPx
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2024/1/8
 * @description
 */
class CameraSelectorView: FrameLayout {

    private val cameraMap = HashMap<String, CameraInfoWrapper>()

    private val sizeFpsMap = HashMap<Size, List<FPS>>()

    private val cameraList = ArrayList<CameraInfoWrapper>()
    lateinit var currentCamera: CameraInfoWrapper
        private set

    private val fpsList = ArrayList<FPS>()
    lateinit var currentFps: FPS
        private set

    private val sizeList = ArrayList<Size>()
    lateinit var currentSize: Size
        private set

    private lateinit var cameraAdapter: CameraSpinnerAdapter
    private lateinit var sizeAdapter: ArrayAdapter<Size>
    private lateinit var fpsAdapter: ArrayAdapter<FPS>

    var onConfigChangedListener: ((cameraInfoWrapper: CameraInfoWrapper, fps: FPS, size: Size) -> Unit)? = null

    private lateinit var cameraSpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var sizeSpinner: Spinner

    private var cameraSpinnerRect = Rect()
    private var fpsSpinnerRect = Rect()
    private var sizeSpinnerRect = Rect()

    private val intervalV = 5
    private val intervalH = 5

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        cameraSpinner = Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(cameraSpinner)

        fpsSpinner = Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(fpsSpinner)

        sizeSpinner = Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        addView(sizeSpinner)

        initViews()
    }

    private fun initViews() {
        cameraAdapter = CameraSpinnerAdapter()
        sizeAdapter = ArrayAdapter(context, R.layout.item_camera_simple, R.id.tv)
        fpsAdapter = ArrayAdapter(context, R.layout.item_camera_simple, R.id.tv)

        cameraSpinner.adapter = cameraAdapter
        cameraSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val camera = cameraList[position]
                if (camera != currentCamera) {
                    updateCamera(camera)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }

        sizeSpinner.adapter = sizeAdapter
        sizeSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val size = sizeList[position]
                if (size != currentSize) {
                    currentSize = size
                    updateFpsBySize()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }

        fpsSpinner.adapter = fpsAdapter
        fpsSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val fps = fpsList[position]
                if (fps != currentFps) {
                    currentFps = fps
                    notifyConfigChanged()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var height = MeasureSpec.getSize(heightMeasureSpec)
        var heightMode = MeasureSpec.getMode(heightMeasureSpec)

        measureChildren(widthMeasureSpec, heightMeasureSpec)

        val views = arrayOf(cameraSpinner, fpsSpinner, sizeSpinner)
        val rectangles = arrayOf(cameraSpinnerRect, fpsSpinnerRect, sizeSpinnerRect)

        val availableLeft = paddingLeft
        val availableRight = width - paddingRight
        val availableTop = paddingTop
        var left = availableLeft
        var top = availableTop
        var lineBottom = top
        for (i in views.indices) {
            val view = views[i]
            val rect = rectangles[i]
            rect.set(0, 0, view.measuredWidth, view.measuredHeight)
            rect.offsetTo(left, top)
            if (rect.right > availableRight) {
                if (left != availableLeft) {
                    left = availableLeft
                    top = lineBottom + dpToPx(context, intervalV.toFloat()).toInt()
                    rect.offsetTo(left, top)
                }
            }
            left = rect.right + dpToPx(context, intervalH.toFloat()).toInt()
            lineBottom = Math.max(lineBottom, rect.bottom)
        }

        setMeasuredDimension(width, lineBottom + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val views = arrayOf(cameraSpinner, fpsSpinner, sizeSpinner)
        val rectangles = arrayOf(cameraSpinnerRect, fpsSpinnerRect, sizeSpinnerRect)

        for (i in views.indices) {
            val view = views[i]
            val rect = rectangles[i]
            view.layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }


    fun setCameras(cameras: Collection<CameraInfoWrapper>) {
        cameraMap.clear()
        cameraList.clear()
        for (camera in cameras) {
            cameraMap[camera.cameraID] = camera
        }
        cameraList.addAll(cameraMap.values)
        cameraAdapter.clearData()
        cameraAdapter.setData(cameraList)
        val cameraID = selectCameraID(cameraMap, CameraCharacteristics.LENS_FACING_BACK, true)
        updateCamera(cameraMap[cameraID]!!)
    }

    private fun updateCamera(camera: CameraInfoWrapper) {
        currentCamera = camera
        updateSizeByCamera()
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

        if (this::currentSize.isInitialized) {
            if (!sizeFpsMap.contains(currentSize)) {
                currentSize = sizeList[0]
            }
        } else {
            currentSize = sizeList[0]
        }

        sizeAdapter.clear()
        sizeAdapter.addAll(sizeList)

        updateFpsBySize()
    }

    private fun updateFpsBySize() {
        fpsList.clear()
        sizeFpsMap[currentSize]?.let {
            fpsList.addAll(it)
        }

        if (this::currentFps.isInitialized) {
            if (!fpsList.contains(currentFps)) {
                currentFps = fpsList[0]
            }
        } else {
            currentFps = fpsList[0]
        }

        fpsAdapter.clear()
        fpsAdapter.addAll(fpsList)

        notifyConfigChanged()
    }


    private fun notifyConfigChanged() {
        onConfigChangedListener?.invoke(currentCamera, currentFps, currentSize)
    }

    fun setCamera(camera: CameraInfoWrapper) {
        updateCamera(camera)
    }

    fun setSize(size: Size) {
        if (!sizeList.contains(size)) {
            return
        }
        currentSize = size
        sizeSpinner.setSelection(sizeList.indexOf(currentSize))
        updateFpsBySize()
    }

    fun setFps(fps: FPS) {
        if (!fpsList.contains(fps)) {
            return
        }
        currentFps = fps
        fpsSpinner.setSelection(fpsList.indexOf(fps))
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