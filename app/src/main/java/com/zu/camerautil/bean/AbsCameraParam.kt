package com.zu.camerautil.bean

import java.lang.ref.WeakReference

typealias ValueListener<A> = ((value: A?) -> Unit)
typealias AutoModeListener = ((isAuto: Boolean) -> Unit)
abstract class AbsCameraParam<T> {
    protected val valueListeners = ArrayList<WeakReference<ValueListener<T>>>()
    protected val autoModeListeners = ArrayList<WeakReference<AutoModeListener>>()

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

    open var autoMode: Boolean = false
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
            val weakRef = iterator.next()
            weakRef.get()?.invoke(value) ?: kotlin.run {
                iterator.remove()
            }
        }
    }

    protected open fun notifyAutoModeChanged() {
        val iterator = autoModeListeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            ref.get()?.invoke(autoMode) ?: kotlin.run {
                iterator.remove()
            }
        }
    }

    open fun addValueListener(listener: ValueListener<in T>) {
        val listenerRef = WeakReference(listener)
        valueListeners.add(listenerRef)
    }

    open fun removeValueListener(listener: ValueListener<T>) {
        valueListeners.removeIf {
            it.get() == null || it.get() == listener
        }
    }

    open fun addAutoModeListener(listener: AutoModeListener) {
        val ref = WeakReference(listener)
        autoModeListeners.add(ref)
    }

    open fun removeAutoModeListener(listener: AutoModeListener) {
        autoModeListeners.removeIf {
            it.get() == null || it.get() == listener
        }
    }
}