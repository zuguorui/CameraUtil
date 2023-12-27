package com.zu.camerautil.util

import android.graphics.Bitmap
import android.media.Image

/**
 * @author zuguorui
 * @date 2023/12/27
 * @description
 */
object ImageConverter {

    init {
        System.loadLibrary("native-lib")
    }

    fun convertYUV_420_888_to_bitmap(image: Image): Bitmap {
        return nYUV_420_888_to_bitmap(image)
    }

    external fun nYUV_420_888_to_bitmap(image: Image): Bitmap
}