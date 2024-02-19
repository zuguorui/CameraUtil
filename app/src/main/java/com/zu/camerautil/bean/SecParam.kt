package com.zu.camerautil.bean

import com.zu.camerautil.copyToArray
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.exp

class SecParam: RangeParam<Long>(CameraParamID.SEC) {

    private var values = SEC_ARRAY

    private var sec: Sec? = null

    override var value: Long? = null
        set(value) {
            sec = value?.let {
                findNearestSec(it, values)
            } ?: null
            val realValue = sec?.exposureTime
            val diff = realValue != field
            field = realValue
            if (diff) {
                Timber.d("value: $value")
                notifyValueChanged()
            }
        }



    override var min: Long? = null
        set(value) {
            Timber.d("min: $value")
            val diff = value != field
            field = value
            if (diff) {
                updateValues()
                notifyRangeChanged()
            }
        }

    override var max: Long? = null
        set(value) {
            Timber.d("max: $value")
            val diff = value != field
            field = value
            if (diff) {
                updateValues()
                notifyRangeChanged()
            }
        }

    override val name: String
        get() = "Sec"
    override val isModal: Boolean
        get() = true
    override val valueName: String
        get() = sec?.let {
            "1/${it.shutterSpeed}"
        } ?: "N/A"
    override val modeName: String
        get() = if (autoMode) "A" else "M"
    override val isDiscrete: Boolean
        get() = true
    override val uiStep: Float
        get() = 1.0f / (values.size - 1)

    override fun uiValueToValue(uiValue: Float): Long {
        val index = (uiValue / uiStep).toInt()
        return values[index].exposureTime
    }

    override fun valueToUiValue(value: Long): Float {
        val index = findNearestSecIndex(value, values)
        return index * uiStep
    }

    override fun valueToUiName(value: Long): String {
        val sec = findNearestSec(value, values)
        return "1/${sec.shutterSpeed}"
    }

    private fun findNearestSec(exposureTime: Long, secArray: Array<Sec>): Sec {
        return secArray[findNearestSecIndex(exposureTime, secArray)]
    }

    private fun findNearestSecIndex(exposureTime: Long, secArray: Array<Sec>): Int {
        var begin = 0
        var end = secArray.size - 1
        var mid: Int
        while (begin < end - 1) {
            mid = (begin + end) / 2
            val c = (secArray[mid].exposureTime - exposureTime).toInt()
            if (c == 0) {
                return mid
            } else if (c > 0) {
                end = mid
            } else {
                begin = mid
            }
        }
        val m1 = Math.abs(secArray[begin].exposureTime - exposureTime)
        val m2 = Math.abs(secArray[end].exposureTime - exposureTime)
        return if (m1 <= m2) {
            begin
        } else {
            end
        }
    }

    private fun updateValues() {
        val min = min ?: SEC_ARRAY.first().exposureTime
        val max = max ?: SEC_ARRAY.last().exposureTime

        val ret = ArrayList<Sec>()

        for (sec in SEC_ARRAY) {
            if (sec.exposureTime in min..max) {
                ret.add(sec)
            }
        }
        values = ret.copyToArray()
        Timber.d("update values: min = ${values.first()}, max = ${values.last()}")
    }

    companion object {
        private const val NANO_SECONDS = 1_000_000_000
        private val SHUTTER_SPEED_ARRAY = intArrayOf(
            5,
            6,
            7,
            10,
            12,
            15,
            24,
            25,
            30,
            40,
            50,
            60,
            70,
            80,
            90,
            100,
            110,
            120,
            140,
            160,
            180,
            200,
            240,
            300,
            350,
            400,
            450,
            500,
            550,
            600,
            650,
            700,
            750,
            800,
            850,
            900,
            1000,
            1100,
            1200,
            1300,
            1400,
            1500,
            1600,
            1700,
            1800,
            1900,
            2000,
            2200,
            2400,
            2600,
            2800,
            3000,
            3300,
            3600,
            3900,
            4200,
            4600,
            5000,
            5500,
            5600
        )
        // 按照曝光时间由小到大
        private val SEC_ARRAY = Array<Sec>(SHUTTER_SPEED_ARRAY.size) {
            Sec(SHUTTER_SPEED_ARRAY[SHUTTER_SPEED_ARRAY.size - 1 - it])
        }
    }

    private data class Sec(
        val shutterSpeed: Int
    ) {
        val exposureTime: Long = (NANO_SECONDS.toDouble() / shutterSpeed).toLong()
        override fun toString(): String {
            return "1/$shutterSpeed, $exposureTime"
        }
    }
}