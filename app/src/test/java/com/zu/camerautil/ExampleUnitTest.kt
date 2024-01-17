package com.zu.camerautil

import android.util.Log
import com.zu.camerautil.camera.WbUtil
import org.junit.Test

import org.junit.Assert.*
import timber.log.Timber

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    companion object {
        private const val TAG = "UnitTest"
    }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }


    @Test
    fun test_temp() {
        val temp = 3000
        val tint = 0

        val vector = WbUtil.computeRggbChannelVector(temp, tint)

        val (newTemp, newTint) = WbUtil.computeTempAndTint(vector)

        assert(temp == newTemp && tint == newTint) {
            Log.e(TAG, "temp = $temp, tint = $tint, newTemp = $newTemp, newTint = $newTint")
        }
    }
}