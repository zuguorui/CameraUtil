package com.zu.camerautil.util

import androidx.lifecycle.LiveData
import timber.log.Timber
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class MutableListLiveData<T>(private var list: MutableList<T> = ArrayList()): LiveData<MutableList<T>>(list), MutableList<T> by list {
    override fun add(element: T): Boolean {
        list.add(element)
        value = list
        return true
    }

    override fun add(index: Int, element: T) {
        list.add(index, element)
        value = list
    }

    override fun addAll(elements: Collection<T>): Boolean {
        Timber.d("addAll")
        list.addAll(elements)
        value = list
        return true
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        Timber.d("addAll")
        list.addAll(index, elements)
        value = list
        return true
    }

    override fun clear() {
        list.clear()
        value = list
    }

    override fun remove(element: T): Boolean {
        val ret = list.remove(element)
        if (ret) {
            value = list
        }
        return ret
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val ret = list.removeAll(elements)
        if (ret) {
            value = list
        }
        return ret
    }

    override fun removeAt(index: Int): T {
        val ret = list.removeAt(index)
        value = list
        return ret
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): MutableList<T> {
        return this
    }
}