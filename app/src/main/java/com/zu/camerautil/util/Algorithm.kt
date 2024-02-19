package com.zu.camerautil.util

fun <T> findNearest(values: Array<T>, value: T, subtract: (o1: T, o2: T) -> Int): Int {
    var begin = 0
    var end = values.size - 1
    var mid: Int
    while (begin < end - 1) {
        mid = (begin + end) / 2
        val c = subtract(values[mid], value)
        if (c == 0) {
            return mid
        } else if (c > 0) {
            begin = mid
        } else {
            end = mid
        }

    }

    val m1 = Math.abs(subtract(values[begin], value))
    val m2 = Math.abs(subtract(values[end], value))

    return if (m1 <= m2) {
        begin
    } else {
        end
    }

}