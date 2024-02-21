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

    abstract val isDiscrete: Boolean
    abstract val uiStep: Float

    abstract fun uiValueToValue(uiValue: Float): T
    abstract fun valueToUiValue(value: T): Float

    open fun valueToUiName(value: T): String {
        return value.toString()
    }
}