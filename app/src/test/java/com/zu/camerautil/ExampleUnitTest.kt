package com.zu.camerautil

import android.util.Log
import com.zu.camerautil.camera.computeRggbChannelVector_my
import com.zu.camerautil.camera.computeTempAndTint_my
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
        val temp = 0.2f
        val tint = 0.5f

        val vector = computeRggbChannelVector_my(temp, tint)

        val (newTemp, newTint) = computeTempAndTint_my(vector)

        assert(temp == newTemp && tint == newTint) {
            Log.e(TAG, "temp = $temp, tint = $tint, newTemp = $newTemp, newTint = $newTint")
        }
    }
}