package com.zu.camerautil.bean

import com.zu.camerautil.copyToArray
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
                notifyValueChanged()
            }
        }



    override var min: Long? = null
        set(value) {
            val diff = value != field
            field = value
            if (diff) {
                updateValues()
                notifyRangeChanged()
            }
        }

    override var max: Long? = null
        set(value) {
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
        get() = sec?.toString() ?: "N/A"
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
        for (i in values.indices) {
            if (values[i].exposureTime == value) {
                return i * uiStep
            }
        }
        return 0f
    }

    private fun findNearestSec(exposureTime: Long, secArray: Array<Sec>): Sec {
        var ret: Sec = secArray[0]
        var diff = Math.abs(ret.exposureTime - exposureTime)
        for (i in 1 until secArray.size) {
            val sec = secArray[i]
            if (Math.abs(sec.exposureTime - exposureTime) < diff) {
                ret = sec
            }
        }
        return ret
    }

    private fun updateValues() {
        val min = min ?: SEC_ARRAY.last().exposureTime
        val max = max ?: SEC_ARRAY.first().exposureTime

        val ret = ArrayList<Sec>()

        for (sec in SEC_ARRAY) {
            if (sec.exposureTime in min..max) {
                ret.add(sec)
            }
        }
        values = ret.copyToArray()
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
        private val SEC_ARRAY = Array<Sec>(SHUTTER_SPEED_ARRAY.size) {
            Sec(SHUTTER_SPEED_ARRAY[it])
        }
    }

    private data class Sec(
        val shutterSpeed: Int
    ) {
        val exposureTime: Long = (NANO_SECONDS.toDouble() / shutterSpeed).toLong()
        override fun toString(): String {
            return "1/$shutterSpeed"
        }
    }
}