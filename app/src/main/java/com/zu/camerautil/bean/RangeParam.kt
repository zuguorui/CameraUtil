package com.zu.camerautil.bean

import java.lang.ref.WeakReference

typealias OnRangeChangedListener<A> = ((min: A?, max: A?) -> Unit)
abstract class RangeParam<T: Number>(val id: CameraParamID): AbsCameraParam<T>(){

    protected val rangeListeners = ArrayList<WeakReference<OnRangeChangedListener<T>>>()

    var max: T? = null
        protected set(value) {
            val diff = value == field
            field = value
            if (diff) {

            }
        }
    var min: T? = null
        protected set(value) {
            val diff = value == field
            field = value
            if (diff) {

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

    fun addOnRangeChangedListener(listener: OnRangeChangedListener<T>) {
        val ref = WeakReference(listener)
        rangeListeners.add(ref)
    }

    fun removeOnRangeChangedListener(listener: OnRangeChangedListener<T>) {
        rangeListeners.removeIf {
            it.get() == null || it.get() == listener
        }
    }

    abstract val uiMin: Float
    abstract val uiMax: Float
    abstract val isDiscrete: Boolean
    abstract val uiStep: Float
    abstract fun onUiValueChanged(uiValue: Float)
}