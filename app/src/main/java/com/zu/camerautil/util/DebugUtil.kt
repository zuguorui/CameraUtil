package com.zu.camerautil.util

import android.util.Log

/**
 * @author zuguorui
 * @date 2024/5/22
 * @description
 */

val StackTraceElement.simpleClassName: String
    get() = this.className.run {
        substring(lastIndexOf('.') + 1)
    }

val StackTraceElement.topSimpleClassName: String
    get() {
        val simpleClassName = this.simpleClassName
        val classSeparatorIndex = simpleClassName.indexOf('$')
        return if (classSeparatorIndex > 0) {
            simpleClassName.substring(0, classSeparatorIndex)
        } else {
            simpleClassName
        }
    }

fun generateCallerTrace(): StackTraceElement {
    val stackTraceElements = Throwable().stackTrace
    var target: StackTraceElement? = null
    for (i in stackTraceElements.indices) {
        val s = stackTraceElements[i]
        if (s.topSimpleClassName != "DebugUtilKt") {
            target = stackTraceElements[i]
            break
        }
    }
    return target!!
}

fun measureTimeCost(block: () -> Any?): Long {
    val startTime = System.currentTimeMillis()
    block.invoke()
    val endTime = System.currentTimeMillis()
    return endTime - startTime
}

inline fun <T> printTimeCost(block: () -> T): T {
    val startTime = System.currentTimeMillis()
    val t = block.invoke()
    val endTime = System.currentTimeMillis()
    val st = generateCallerTrace()
    Log.d(st.topSimpleClassName, "${st.methodName}() cost ${endTime - startTime}ms")
    return t
}
