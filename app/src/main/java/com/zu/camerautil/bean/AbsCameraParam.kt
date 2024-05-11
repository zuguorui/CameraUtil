package com.zu.camerautil.bean

import java.lang.ref.WeakReference

// 值监听器
typealias ValueListener<A> = ((value: A?) -> Unit)
// 自动模式监听器
typealias AutoModeListener = ((isAuto: Boolean) -> Unit)
abstract class AbsCameraParam<T> {
    protected val valueListeners = ArrayList<ValueListener<T>>()
    protected val autoModeListeners = ArrayList<AutoModeListener>()

    // 参数名称，显示在UI上的
    abstract val name: String
    // 是否是模式化的。如果是，代表可以切换模式。目前只支持两种模式。
    abstract val isModal: Boolean
    // 值的UI显示名称。
    abstract val valueName: String
    // 模式名称。显示在UI上的。
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