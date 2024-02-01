package com.zu.camerautil.bean

import androidx.lifecycle.MutableLiveData
import com.zu.camerautil.util.MutableListLiveData
import java.lang.ref.WeakReference

typealias OnValuesChangedListener<A> = ((values: List<A>) -> Unit)
abstract class SelectionParam<T>(val id: CameraParamID): AbsCameraParam<T>() {
    protected val valuesListeners = ArrayList<WeakReference<OnValuesChangedListener<T>>>()

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

    fun addOnValuesChangedListener(listener: OnValuesChangedListener<T>) {
        val ref = WeakReference(listener)
        valuesListeners.add(ref)
    }

    fun removeOnValuesChangedListener(listener: OnValuesChangedListener<T>) {
        valuesListeners.removeIf {
            it.get() == null || it.get() == listener
        }
    }

    override fun toString(): String {
        return "$id: value = $value"
    }
}