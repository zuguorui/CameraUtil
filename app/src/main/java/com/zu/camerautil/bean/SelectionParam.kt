package com.zu.camerautil.bean

import com.zu.camerautil.util.MutableListLiveData
import timber.log.Timber
import java.lang.ref.WeakReference

typealias ValuesListener<A> = ((values: List<A>) -> Unit)
abstract class SelectionParam<T>(val id: CameraParamID): AbsCameraParam<T>() {
    protected val valuesListeners = ArrayList<WeakReference<ValuesListener<out T>>>()

    protected val valueLiveData = MutableListLiveData<T>().apply {
        observeForever {
            notifyValuesChanged()
        }
    }
    val values: MutableList<T> by valueLiveData

    protected fun notifyValuesChanged() {
        val iterator = valuesListeners.iterator()
        while (iterator.hasNext()) {
            val weakRef = iterator.next()
            weakRef.get()?.invoke(values) ?: kotlin.run {
                iterator.remove()
            }
        }
    }

    fun addOnValuesChangedListener(listener: ValuesListener<T>) {
        val ref = WeakReference(listener)
        valuesListeners.add(ref)
    }

    fun removeOnValuesChangedListener(listener: ValuesListener<T>) {
        valuesListeners.removeIf {
            it.get() == null || it.get() == listener
        }
    }

    open fun valueToSelectionElement(t: T): UiElement {
        return UiElement(t.toString(), values.indexOf(t))
    }
    open fun selectionElementToValue(element: UiElement): T {
        return values[element.id]
    }

    override fun toString(): String {
        return "$id: value = $value, selectionCount = ${values.size}"
    }
}