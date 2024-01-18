package com.zu.camerautil

import android.hardware.camera2.params.RggbChannelVector
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zu.camerautil.camera.WbUtil

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import timber.log.Timber

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    companion object {
        private const val TAG = "AndroidTest"
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.zu.camerautil", appContext.packageName)
    }

    @Test
    fun test_rggb_vector() {
        val temp = 2000
        val tint = 0

        val vector = WbUtil.computeRggbChannelVector(temp, tint)

        val (newTemp, newTint) = WbUtil.computeTempAndTint(vector)

        assert(Math.abs(temp - newTemp) < 0.001f && Math.abs(tint - newTint) < 0.001f) {
            Log.e(TAG, "temp = $temp, tint = $tint, newTemp = $newTemp, newTint = $newTint")
            val sb = String.format("%.2f, %.2f, %.2f, %.2f", vector.red, vector.greenOdd, vector.greenEven, vector.blue)
            Log.e(TAG, "rggb channel vector: $sb")
        }
    }

    @Test
    fun test_temp_tint() {
        val vector = RggbChannelVector(2.42f, 1.00f, 1.00f, 1.69f)
        val (temp, tint) = WbUtil.computeTempAndTint(vector)
        Log.d(TAG, "temp = $temp, tint = $tint")
        assert(true)
    }

    @Test
    fun test_temp() {
        val temp = 4762
        val vector = WbUtil.computeRggbChannelVector(temp)
        Log.d(TAG, "vector = [${vector.red}, ${vector.greenOdd}, ${vector.blue}]")
        val newTemp = WbUtil.computeTemp(vector)
        val newVector = WbUtil.computeRggbChannelVector(newTemp)
        Log.d(TAG, "newVector = [${newVector.red}, ${newVector.greenOdd}, ${newVector.blue}]")
        assert(temp == newTemp) {
            Log.e(TAG, "temp = $temp, newTemp = $newTemp")
        }
        assert(vector == newVector) {
            Log.e(TAG, "vector = [${vector.red}, ${vector.greenOdd}, ${vector.blue}]")
            Log.e(TAG, "newVector = [${newVector.red}, ${newVector.greenOdd}, ${newVector.blue}]")
        }
    }
}