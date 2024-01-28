package com.zu.camerautil.bean

import androidx.annotation.FloatRange

open class RangeParam<T: Number>(val id: CameraParamID): ICameraParam<T>{
    var max: T? = null
    var min: T? = null
    var current: T? = null

    open fun translatePercentToValue(@FloatRange(from = 0.0, to = 1.0) percent: Float): T {
        val max = max!!
        val min = min!!
        return when (max) {
            is Short -> {
                val maxValue = max as Short
                val minValue = min as Short
                (percent * (maxValue - minValue) + minValue).toInt().toShort() as T
            }
            is Int -> {
                val maxValue = max as Int
                val minValue = min as Int
                (percent * (maxValue - minValue) + minValue).toInt() as T
            }
            is Long -> {
                val maxValue = max as Long
                val minValue = min as Long
                (percent * (maxValue - minValue) + minValue).toLong() as T
            }
            is Float -> {
                val maxValue = max as Float
                val minValue = min as Float
                (percent * (maxValue - minValue) + minValue) as T
            }
            is Double -> {
                val maxValue = max as Double
                val minValue = min as Double
                (percent * (maxValue - minValue) + minValue) as T
            }
            else -> {
                throw IllegalArgumentException("unsupported type ${max.javaClass}")
            }
        }
    }
    override fun toString(): String {
        return "$id: range = [$min, $max], current = $current"
    }
}