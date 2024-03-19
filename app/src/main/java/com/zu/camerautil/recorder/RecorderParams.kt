package com.zu.camerautil.recorder

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import java.io.File
import java.io.FileDescriptor

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
    val outputFile: File?,
    val outputUri: Uri?,
    val viewOrientation: Int,
    val sensorOrientation: Int,
    val facing: Int
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
