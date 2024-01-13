package com.zu.camerautil.recorder

import android.util.Size
import java.io.File

/**
 * @author zuguorui
 * @date 2024/1/5
 * @description
 */
data class RecorderParams(
    val title: String,
    val resolution: Size,
    val outputFps: Int,
    val inputFps: Int,
    val sampleRate: Int,
    val outputFile: File,
    val rotation: Int
) {
    override fun toString(): String {
        return """
            RecordParams {
                resolution: $resolution
                outputFps: $outputFps
                inputFps: $inputFps
            }
        """.trimIndent()
    }
}
