package com.zu.camerautil.util

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * @author zuguorui
 * @date 2024/1/11
 * @description
 */

fun <T> waitCallbackResult(block: (receiver: CallbackResultReceiver<T>) -> Any): T {
    val receiver = CallbackResultReceiver<T>()
    block(receiver)
    return receiver.get()
}

class CallbackResultReceiver<T> {
    private var isCompleted = false

    private var result: T? = null

    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    fun resume(t: T) {
        result = t
        isCompleted = true
        condition.signalAll()
    }

    fun get(): T {
        if (!isCompleted) {
            condition.await()
        }
        return result!!
    }

    fun get(timeout: Long): T {
        if (!isCompleted) {
            condition.await(timeout, TimeUnit.MILLISECONDS)
        }
        return result!!
    }
}