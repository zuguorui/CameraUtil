package com.zu.camerautil.view

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import com.zu.camerautil.bean.AbsCameraParam
import com.zu.camerautil.bean.AutoModeListener
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.bean.FlashParam
import com.zu.camerautil.bean.ISOParam
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.bean.SecParam
import com.zu.camerautil.bean.SelectionParam
import com.zu.camerautil.bean.TempParam
import com.zu.camerautil.bean.TintParam
import com.zu.camerautil.bean.ValueListener
import com.zu.camerautil.bean.WbModeParam
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.WbUtil
import timber.log.Timber

class CameraParamsView: AbsCameraParamView {

    private val viewMap = HashMap<CameraParamID, ParamView>()
    private val panelMap = HashMap<CameraParamID, EasyLayoutPopupWindow>()
    private val paramMap = HashMap<CameraParamID, AbsCameraParam<Any>>()
    private val valueListenerMap = HashMap<CameraParamID, ArrayList<ValueListener<Any>>>()
    private val autoModeListenerMap = HashMap<CameraParamID, ArrayList<AutoModeListener>>()

    private val layoutInflater: LayoutInflater

    private var currentLens: CameraInfoWrapper? = null
    private var currentSize: Size? = null
    private var currentFps: FPS? = null

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : this(context, attributeSet, defStyleAttr, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        layoutInflater = LayoutInflater.from(context)
        initParamViews()
    }

    private fun initParamViews() {
        val viewList = ArrayList<View>()
        initParam(CameraParamID.SEC, SecParam::class.java)
        viewList.add(viewMap[CameraParamID.SEC]!!)

        initParam(CameraParamID.ISO, ISOParam::class.java)
        viewList.add(viewMap[CameraParamID.ISO]!!)

        initParam(CameraParamID.WB_MODE, WbModeParam::class.java)
        viewList.add(viewMap[CameraParamID.WB_MODE]!!)

        initParam(CameraParamID.FLASH_MODE, FlashParam::class.java)
        viewList.add(viewMap[CameraParamID.FLASH_MODE]!!)

        initParam(CameraParamID.TEMP, TempParam::class.java)
        viewList.add(viewMap[CameraParamID.TEMP]!!)

        initParam(CameraParamID.TINT, TintParam::class.java)
        viewList.add(viewMap[CameraParamID.TINT]!!)

        setItems(viewList)
    }

    private fun <T> initParam(paramID: CameraParamID, paramClass: Class<T>) {
        val paramView = ParamView(context).apply {
            setOnClickListener {
                panelMap[paramID]?.let {
                    it.show(this, paramPanelPopupGravity)
                }
            }
        }

        val constructor = paramClass.getConstructor()
        val param = constructor.newInstance()

        if (param is RangeParam<*>) {

            val popupWindow = RangeParamPopupWindow(context, paramID)

            param.apply {
                addValueListener {
                    val listeners = valueListenerMap[paramID] ?: return@addValueListener
                    val iterator = listeners.iterator()
                    while (iterator.hasNext()) {
                        val listener = iterator.next()
                        listener.invoke(it)
                    }
                }

                addAutoModeListener {
                    val listeners = autoModeListenerMap[paramID] ?: return@addAutoModeListener
                    val iterator = listeners.iterator()
                    while (iterator.hasNext()) {
                        val listener = iterator.next()
                        listener.invoke(it)
                    }
                }
            }

            popupWindow.param = param as RangeParam<Any>
            panelMap[paramID] = popupWindow
        } else if (param is SelectionParam<*>) {
            val popupWindow = SelectionParamPopupWindow(context, paramID)
            param.apply {
                addValueListener {
                    val listeners = valueListenerMap[paramID] ?: return@addValueListener
                    val iterator = listeners.iterator()
                    while (iterator.hasNext()) {
                        val listener = iterator.next();
                        listener.invoke(it)
                    }
                }
            }
            popupWindow.param = param as SelectionParam<Any>
            panelMap[paramID] = popupWindow
        }

        paramView.param = param as AbsCameraParam<Any>

        viewMap[paramID] = paramView
        paramMap[paramID] = param

    }

    fun addValueListener(paramID: CameraParamID, listener: ValueListener<Any>) {
        val list = valueListenerMap[paramID] ?: kotlin.run {
            val list = ArrayList<ValueListener<Any>>()
            valueListenerMap[paramID] = list
            list
        }
        list.add(listener)
    }

    fun removeValueListener(paramID: CameraParamID, listener: ValueListener<Any>) {
        val list = valueListenerMap[paramID] ?: return
        list.removeIf {
            it == listener
        }
    }

    fun addAutoModeListener(paramID: CameraParamID, listener: AutoModeListener) {
        val list = autoModeListenerMap[paramID] ?: kotlin.run {
            val list = ArrayList<AutoModeListener>()
            autoModeListenerMap[paramID] = list
            list
        }
        list.add(listener)
    }

    fun removeAutoModeListener(paramID: CameraParamID, listener: AutoModeListener) {
        val list = autoModeListenerMap[paramID] ?: return
        list.removeIf {
            it == listener
        }
    }

    fun setCameraConfig(camera: CameraInfoWrapper, size: Size, fps: FPS) {
        currentLens = camera
        currentSize = size
        currentFps = fps
        updateParams()
    }

    fun isParamAuto(paramID: CameraParamID): Boolean {
        val param = paramMap[paramID] ?: return true
        return param.isAutoMode
    }

    fun setParamAuto(paramID: CameraParamID, auto: Boolean) {
        val param = paramMap[paramID] ?: return
        param.isAutoMode = auto
    }

    fun setParamEnable(paramID: CameraParamID, enable: Boolean) {
        val paramView = viewMap[paramID] ?: return
        val paramPanel = panelMap[paramID] ?: return
        //paramView.isEnabled = enable
        paramPanel.isEnabled = enable
    }

    fun setParamValue(paramID: CameraParamID, value: Any) {
        val current = paramMap[paramID]
        if (value != current) {
            paramMap[paramID]?.value = value
        }
    }

    fun getParamValue(paramID: CameraParamID): Any? {
        return paramMap[paramID]?.value
    }

    private fun updateParams() {
        updateSecParam()
        updateISOParam()
        updateWbModeParam()
        updateFlashModeParam()
        updateTempParam()
        updateTintParam()
    }

    private fun updateSecParam() {
        val lens = currentLens ?: return
        val fps = currentFps ?: return

        val max = 1_000_000_000 / fps.value

        val secParam = paramMap[CameraParamID.SEC]!! as SecParam
        secParam.min = lens.exposureRange!!.lower
        secParam.max = max.toLong()
    }

    private fun updateISOParam() {
        val lens = currentLens ?: return
        val isoParam = paramMap[CameraParamID.ISO]!! as ISOParam
        if (isoParam.value == null || isoParam.value!! < lens.isoRange!!.lower) {
            isoParam.value = lens.isoRange!!.lower
        }
        isoParam.max = lens.isoRange!!.upper
        isoParam.min = lens.isoRange!!.lower
    }

    private fun updateWbModeParam() {
        val lens = currentLens ?: return
        val wbModeParam = paramMap[CameraParamID.WB_MODE]!! as WbModeParam

        val currentMode = wbModeParam.value
        val modeList = ArrayList<Int>()
        lens.awbModes!!.forEach {
            modeList.add(it)
        }

        wbModeParam.values.clear()
        wbModeParam.values.addAll(modeList)
        if (!modeList.contains(currentMode)) {
            wbModeParam.value = CameraCharacteristics.CONTROL_AWB_MODE_AUTO
        }
    }

    private fun updateFlashModeParam() {
        val lens = currentLens ?: return
        val flashModeParam = paramMap[CameraParamID.FLASH_MODE]!! as FlashParam

        val currentMode = flashModeParam.value

        val modeList = ArrayList<FlashUtil.FlashMode>()
        lens.flashModes!!.forEach {
            modeList.add(it)
        }

        flashModeParam.values.clear()
        flashModeParam.values.addAll(modeList)

        if (!modeList.contains(currentMode)) {
            flashModeParam.value = FlashUtil.FlashMode.OFF
        }
    }

    private fun updateTempParam() {
        val tempParam = paramMap[CameraParamID.TEMP]!! as TempParam
        if (tempParam.value != null && tempParam.value!! !in WbUtil.TEMP_RANGE) {
            tempParam.value = WbUtil.TEMP_RANGE.clamp(tempParam.value)
        }
        Timber.d("param temp begin set range")
        tempParam.max = WbUtil.TEMP_RANGE.upper
        tempParam.min = WbUtil.TEMP_RANGE.lower
        Timber.d("param temp end set range")
    }

    private fun updateTintParam() {
        val tintParam = paramMap[CameraParamID.TINT]!! as TintParam
        if (tintParam.value != null && tintParam.value!! !in WbUtil.TINT_RANGE) {
            tintParam.value = WbUtil.TINT_RANGE.clamp(tintParam.value)
        }
        tintParam.max = WbUtil.TINT_RANGE.upper
        tintParam.min = WbUtil.TINT_RANGE.lower
    }

}