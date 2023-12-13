package com.zu.camerautil

import android.util.Rational
import android.util.Size

inline fun <reified T> ArrayList<T>.copyToArray(): Array<T> {
    var result = Array(size){
        get(it)
    }

    return result
}

fun Size.toRational(): Rational = Rational(width, height)

fun Size.area(): Int = width * height
