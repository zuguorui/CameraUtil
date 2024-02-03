package com.zu.camerautil.bean

import java.lang.ref.WeakReference

typealias RangeListener<A> = ((min: A?, max: A?) -> Unit)
abstract class RangeParam<T>(val id: CameraParamID): AbsCameraParam<T>(){

    protected val rangeListeners = ArrayList<WeakReference<RangeListener<T>>>()

    open var max: T? = null
        protected set(value) {
            val diff = value != field
            field = value
            if (diff) {
                notifyRangeChanged()
            }
        }
    open var min: T? = null
        protected set(value) {
            val diff = value != field
            field = value
            if (diff) {
                notifyRangeChanged()
            }
        }

    protected fun notifyRangeChanged() {
        val iterator = rangeListeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            ref.get()?.invoke(min, max) ?: kotlin.run {
                iterator.remove()
            }
        }
    }

    fun addOnRangeChangedListener(listener: RangeListener<T>) {
        val ref = WeakReference(listener)
        rangeListeners.add(ref)
    }

    fun removeOnRangeChangedListener(listener: RangeListener<T>) {
        rangeListeners.removeIf {
            it.get() == null || it.get() == listener
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