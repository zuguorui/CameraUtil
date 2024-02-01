package com.zu.camerautil.bean

import android.graphics.Color
import java.lang.ref.WeakReference

typealias OnValueChangedListener<A> = ((value: A?) -> Unit)
abstract class AbsAdjustableParam<T> {
    protected val valueListeners = ArrayList<WeakReference<OnValueChangedListener<T>>>()
    var value: T? = null
        set(value) {
            val diff = value == field
            field = value
            if (diff) {
                notifyValueChanged()
            }
        }

    protected fun notifyValueChanged() {
        val iterator = valueListeners.iterator()
        while (iterator.hasNext()) {
            val weakRef = iterator.next()
            weakRef.get()?.invoke(value) ?: kotlin.run {
                iterator.remove()
            }
        }
    }

    fun addOnValueChangedListener(listener: OnValueChangedListener<T>) {
        val listenerRef = WeakReference(listener)
        valueListeners.add(listenerRef)
    }

    fun removeOnValueChangedListener(listener: OnValueChangedListener<T>) {
        valueListeners.removeIf {
            it.get() == null || it.get() == listener
        }
    }
    abstract fun valueToUiElement(t: T): AdjustUiElement
}

data class AdjustUiElement(
    val text: String,
    val id: Int,
    val textColor: Int = Color.BLACK,
    val background: Int = Color.WHITE
)