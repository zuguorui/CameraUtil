package com.zu.camerautil.view

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import com.zu.camerautil.bean.AbsCameraParam
import com.zu.camerautil.bean.AutoModeListener
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.bean.ISOParam
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.bean.SecParam
import com.zu.camerautil.bean.SelectionParam
import com.zu.camerautil.bean.ValueListener
import java.lang.ref.WeakReference

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
        initParam(CameraParamID.SEC, SecParam::class.java)
        initParam(CameraParamID.ISO, ISOParam::class.java)

        val viewList = ArrayList<View>().apply {
            addAll(viewMap.values)
        }
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

            val popupWindow = RangeParamPopupWindow(context)

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

        }

        paramView.param = param as AbsCameraParam<Any>

        viewMap[paramID] = paramView
        paramMap[paramID] = param

    }


    private fun initSecParam() {
        val paramView = ParamView(context).apply {
            setOnClickListener {
                panelMap[CameraParamID.SEC]?.let {
                    it.show(this, paramPanelPopupGravity)
                }
            }
        }

        val popupWindow = RangeParamPopupWindow(context)
        val param = SecParam().apply {
            addValueListener {
                val listeners = valueListenerMap[CameraParamID.SEC] ?: return@addValueListener
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val listener = iterator.next()
                    listener.invoke(it)
                }
            }

            addAutoModeListener {
                val listeners = autoModeListenerMap[CameraParamID.SEC] ?: return@addAutoModeListener
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val listener = iterator.next()
                    listener.invoke(it)
                }
            }
        }

        paramView.param = param as AbsCameraParam<Any>
        popupWindow.param = param as RangeParam<Any>

        viewMap[CameraParamID.SEC] = paramView
        panelMap[CameraParamID.SEC] = popupWindow
        paramMap[CameraParamID.SEC] = param
    }

    private fun initISOParam() {
        val paramView = ParamView(context).apply {
            setOnClickListener {
                panelMap[CameraParamID.ISO]?.let {
                    it.show(this, paramPanelPopupGravity)
                }
            }
        }

        val popupWindow = RangeParamPopupWindow(context)
        val param = ISOParam().apply {
            addValueListener {
                val listeners = valueListenerMap[CameraParamID.ISO] ?: return@addValueListener
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val listener = iterator.next()
                    listener.invoke(it)
                }
            }

            addAutoModeListener {
                val listeners = autoModeListenerMap[CameraParamID.ISO] ?: return@addAutoModeListener
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val listener = iterator.next()
                    listener.invoke(it)
                }
            }
        }

        paramView.param = param as AbsCameraParam<Any>
        popupWindow.param = param as RangeParam<Any>

        viewMap[CameraParamID.ISO] = paramView
        panelMap[CameraParamID.ISO] = popupWindow
        paramMap[CameraParamID.ISO] = param
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

    fun setParamValue(paramID: CameraParamID, value: Any) {
        paramMap[paramID]?.value = value
    }

    private fun updateParams() {
        updateSecParam()
        updateISOParam()
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

}