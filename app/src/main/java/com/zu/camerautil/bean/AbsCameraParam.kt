package com.zu.camerautil.bean

import java.lang.ref.WeakReference

typealias ValueListener<A> = ((value: A?) -> Unit)
typealias AutoModeListener = ((isAuto: Boolean) -> Unit)
abstract class AbsCameraParam<T> {
    protected val valueListeners = ArrayList<ValueListener<T>>()
    protected val autoModeListeners = ArrayList<AutoModeListener>()

    abstract val name: String
    abstract val isModal: Boolean
    abstract val valueName: String
    abstract val modeName: String
    open var value: T? = null
        set(value) {
            val diff = value != field
            field = value
            if (diff) {
                notifyValueChanged()
            }
        }

    open var isAutoMode: Boolean = true
        set(value) {
            val diff = field != value
            field = value
            if (diff) {
                notifyAutoModeChanged()
            }
        }

    protected open fun notifyValueChanged() {
        val iterator = valueListeners.iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next()
            listener.invoke(value)
        }
    }

    protected open fun notifyAutoModeChanged() {
        val iterator = autoModeListeners.iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next()
            listener.invoke(isAutoMode)
        }
    }

    open fun addValueListener(listener: ValueListener<in T>) {
        valueListeners.add(listener)
    }

    open fun removeValueListener(listener: ValueListener<T>) {
        valueListeners.removeIf {
            it == listener
        }
    }

    open fun addAutoModeListener(listener: AutoModeListener) {
        autoModeListeners.add(listener)
    }

    open fun removeAutoModeListener(listener: AutoModeListener) {
        autoModeListeners.removeIf {
            it == listener
        }
    }
}