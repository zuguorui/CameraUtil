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



