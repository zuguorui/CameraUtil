package com.zu.camerautil

import android.graphics.Bitmap
import android.media.Image
import androidx.core.graphics.set

/**
 * @author zuguorui
 * @date 2023/12/8
 * @description
 */

fun convert_RGBA_8888_ToBitmap(image: Image): Bitmap? {
    return try {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val plane = planes[0]
        // 这里可能因为ImageReader已执行Close抛出：IllegalStateException: Image is already closed
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val imgWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(imgWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        bitmap
    } catch (e: IllegalStateException) {
        e.printStackTrace()
        null
    }
}

fun convertYPlaneToBitmap(image: Image): Bitmap {
    val yBuffer = image.planes[0].buffer

    val rowStride = IntArray(3) {
        image.planes[it].rowStride
    }

    val pixelStride = IntArray(3) {
        image.planes[it].pixelStride
    }

    val imageWidth = image.width
    val imageHeight = image.height
    val bitmap = Bitmap.createBitmap(imageHeight, imageWidth, Bitmap.Config.ARGB_8888)

    var y: Int
    var colorInt: Int
    for (row in 0 until imageHeight) {
        for (col in 0 until imageWidth) {
            y = yBuffer.get(row * rowStride[0] + col * pixelStride[0]).toInt() and 0x00FF
            colorInt = (0x00FF shl 24) or (y shl 16) or (y shl 8) or y
            bitmap.set(imageHeight - row - 1, col, colorInt)
        }
    }
    return bitmap
}