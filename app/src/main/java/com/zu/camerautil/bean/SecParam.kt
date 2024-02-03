package com.zu.camerautil.bean

import android.util.Rational
import kotlin.math.ceil
import kotlin.math.floor

class SecParam: RangeParam<Long>(CameraParamID.SEC) {

    private var values: Array<Sec>? = null
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
        get() = value?.toString() ?: "N/A"
    override val modeName: String
        get() = if (autoMode) "A" else "M"
    override val isDiscrete: Boolean
        get() = true
    override val uiStep: Float
        get() = TODO("Not yet implemented")

    override fun uiValueToValue(uiValue: Float): Long {
        TODO("Not yet implemented")
    }

    override fun valueToUiValue(value: Long): Float {
        TODO("Not yet implemented")
    }

    private fun updateValues() {

    }

    companion object {
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
        val shutterInterval: Long = ceil(1_000_000_000.0 / shutterSpeed).toLong()
    }
}