package com.zu.camerautil.util

object NeonTest {
    init {
        System.loadLibrary("native-lib")
    }

    external fun doNeonTest()
}