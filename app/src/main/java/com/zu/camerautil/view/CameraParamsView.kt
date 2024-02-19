package com.zu.camerautil.view

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import com.zu.camerautil.bean.AbsCameraParam
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.bean.FPS
import com.zu.camerautil.bean.RangeParam
import com.zu.camerautil.bean.SecParam
import com.zu.camerautil.bean.ValueListener
import java.lang.ref.WeakReference

class CameraParamsView: AbsCameraParamView {

    private val viewMap = HashMap<CameraParamID, ParamView>()
    private val panelMap = HashMap<CameraParamID, EasyLayoutPopupWindow>()
    private val paramMap = HashMap<CameraParamID, AbsCameraParam<Any>>()
    private val listenerMap = HashMap<CameraParamID, ArrayList<WeakReference<ValueListener<Any>>>>()

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
        initSecParams()

        val viewList = ArrayList<View>().apply {
            addAll(viewMap.values)
        }
        setItems(viewList)
    }


    private fun initSecParams() {
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
                val listeners = listenerMap[CameraParamID.SEC] ?: return@addValueListener
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val ref = iterator.next()
                    ref.get()?.invoke(it) ?: kotlin.run {
                        iterator.remove()
                    }
                }
            }
        }

        paramView.param = param as AbsCameraParam<Any>
        popupWindow.param = param as RangeParam<Any>

        viewMap[CameraParamID.SEC] = paramView
        panelMap[CameraParamID.SEC] = popupWindow
        paramMap[CameraParamID.SEC] = param
    }

    fun addValueListener(paramID: CameraParamID, listener: ValueListener<Any>) {
        val ref = WeakReference(listener)
        val list = listenerMap[paramID] ?: kotlin.run {
            val list = ArrayList<WeakReference<ValueListener<Any>>>()
            listenerMap[paramID] = list
            list
        }
        list.add(ref)
    }

    fun removeValueListener(paramID: CameraParamID, listener: ValueListener<Any>) {
        val list = listenerMap[paramID] ?: return
        list.removeIf {
            it.get() == null || it.get() == listener
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
        return param.autoMode
    }

    fun setParamAuto(paramID: CameraParamID, auto: Boolean) {
        val param = paramMap[paramID] ?: return
        param.autoMode = auto
    }

    private fun updateParams() {
        val lens = currentLens ?: return
        val size = currentSize ?: return
        val fps = currentFps ?: return


    }

    private fun updateSecParam() {
        val lens = currentLens ?: return
        val fps = currentFps ?: return

        val secParam = paramMap[CameraParamID.SEC]!!

    }

}