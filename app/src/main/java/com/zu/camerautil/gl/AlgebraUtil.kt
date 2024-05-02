package com.zu.camerautil.gl

import android.opengl.Matrix
import androidx.annotation.IntRange
import java.lang.StringBuilder

fun eye4(): FloatArray {
    return floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}
fun eye(@IntRange(from = 1) n: Int): FloatArray {
    val result = FloatArray(n * n) {
        0f
    }
    for (i in 0 until n) {
        result[i * n + i] = 1f
    }
    return result
}

fun rotate(mat: FloatArray, degree: Int, x: Float, y: Float, z: Float) {
    if (mat.size != 16) {
        throw IllegalArgumentException("<mat> must be 4x4")
    }
    Matrix.rotateM(mat, 0, degree.toFloat(), x, y, z)
}

fun scale(mat: FloatArray, scaleX: Float, scaleY: Float, scaleZ: Float) {
    if (mat.size != 16) {
        throw IllegalArgumentException("<mat> must be 4x4")
    }
    Matrix.scaleM(mat, 0, scaleX, scaleY, scaleZ)
}

fun translate(mat: FloatArray, translateX: Float, translateY: Float, translateZ: Float) {
    if (mat.size != 16) {
        throw IllegalArgumentException("<mat> must be 4x4")
    }
    Matrix.translateM(mat, 0, translateX, translateY, translateZ)
}

fun matToString(mat: FloatArray, rows: Int, cols: Int): String {
    val sb = StringBuilder()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            sb.append(String.format("%.2f", mat[row * cols + col]))
            if (col < cols - 1) {
                sb.append(", ")
            }
        }
        if (row < rows - 1) {
            sb.append("\n")
        }
    }
    return sb.toString()
}



