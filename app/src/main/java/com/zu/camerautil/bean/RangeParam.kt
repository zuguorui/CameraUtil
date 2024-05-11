package com.zu.camerautil.bean

import java.lang.ref.WeakReference

typealias RangeListener<A> = ((min: A?, max: A?) -> Unit)
abstract class RangeParam<T>(val id: CameraParamID): AbsCameraParam<T>(){

    protected val rangeListeners = ArrayList<RangeListener<T>>()

    open var max: T? = null
        set(value) {
            val diff = value != field
            field = value
            if (diff) {
                notifyRangeChanged()
            }
        }
    open var min: T? = null
        set(value) {
            val diff = value != field
            field = value
            if (diff) {
                notifyRangeChanged()
            }
        }

    protected fun notifyRangeChanged() {
        val iterator = rangeListeners.iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next()
            listener.invoke(min, max)
        }
    }

    fun addRangeListener(listener: RangeListener<T>) {
        rangeListeners.add(listener)
    }

    fun removeRangeListener(listener: RangeListener<T>) {
        rangeListeners.removeIf {
            it == listener
        }
    }

    // 是否是步进的，代表值每次变化的最小值
    abstract val isDiscrete: Boolean
    // 如果isDiscrete=true，那么这里要返回步进值
    abstract val uiStep: Float

    // 将UI值转换为实际值
    abstract fun uiValueToValue(uiValue: Float): T
    // 与上一步相反，将实际值转化为UI值
    abstract fun valueToUiValue(value: T): Float
    // 将实际值转化为UI显示名称，例如Sec，实际值是一帧曝光的微秒。但显示到界面是秒的分数。
    open fun valueToUiName(value: T): String {
        return value.toString()
    }
}