package com.zu.camerautil

import android.graphics.Rect
import android.util.Rational
import android.util.Size
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.typeOf

inline fun <reified T> ArrayList<T>.copyToArray(): Array<T> {
    var result = Array(size){
        get(it)
    }

    return result
}

fun Size.toRational(): Rational = Rational(width, height)

fun Size.area(): Int = width * height

fun Rect.toRational(): Rational = Rational(width(), height())

fun FloatArray.toFormattedText(points: Int = 2): String {
    val formatText = "%.${points}f"
    val sb = StringBuilder()
    sb.append("[")
    for (i in indices) {
        sb.append(String.format(formatText, get(i)))
        if (i < size - 1) {
            sb.append(", ")
        }
    }
    sb.append("]")
    return sb.toString()
}

fun <T> Array<T>.toFormattedString(): String {
    val sb = StringBuilder()
    sb.append("[")
    var i = 0
    for (t in iterator()) {
        sb.append("$t")
        if (i < size - 1) {
            sb.append(", ")
        }
        i++
    }
    sb.append("]")
    return sb.toString()
}

fun <T> Collection<T>.toFormattedString(): String {
    val sb = StringBuilder()
    sb.append("[")
    var i = 0
    for (t in iterator()) {
        sb.append("$t")
        if (i < size - 1) {
            sb.append(", ")
        }
        i++
    }
    sb.append("]")
    return sb.toString()
}

fun <T> lockBlock(lock: ReentrantLock, block: () -> T): T {
    lock.lock()
    val t = block()
    lock.unlock()
    return t
}

suspend fun <T> suspendLockBlock(lock: ReentrantLock, block: suspend () -> T): T {
    lock.lock()
    val t = block()
    lock.unlock()
    return t
}




