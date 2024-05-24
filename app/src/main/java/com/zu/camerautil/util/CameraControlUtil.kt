package com.zu.camerautil.util

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest.Builder
import androidx.core.view.get
import com.zu.camerautil.bean.CameraParamID
import com.zu.camerautil.camera.FlashUtil
import com.zu.camerautil.camera.WbUtil
import com.zu.camerautil.view.CameraParamsView
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2024/5/14
 * @description 相机参数操作工具。将参数设置给相机。
 */

fun refreshCameraParams(cameraParamsView: CameraParamsView, builder: Builder) {
    // sec and iso
    val aeAuto = cameraParamsView.isParamAuto(CameraParamID.SEC)
    if (aeAuto) {
        setAe(true, null, null, builder)
    } else {
        val sec = cameraParamsView.getParamValue(CameraParamID.SEC) as Long
        val iso = cameraParamsView.getParamValue(CameraParamID.ISO) as Int
        setAe(false, sec, iso, builder)
    }

    // wb
    val wbMode = cameraParamsView.getParamValue(CameraParamID.WB_MODE) as Int
    Timber.d("wbMode = $wbMode")
    if (wbMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF) {
        val temp = cameraParamsView.getParamValue(CameraParamID.TEMP) as Int
        val tint = cameraParamsView.getParamValue(CameraParamID.TINT) as Int
        setWb(wbMode, temp, tint, builder)
    } else {
        setWb(wbMode, null, null, builder)
    }
}

fun setAe(aeAuto: Boolean, sec: Long?, iso: Int?, builder: Builder) {
    when(aeAuto) {
        true -> {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        else -> {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            setSec(sec!!, builder)
            setIso(iso!!, builder)
        }
    }
}

fun setSec(sec: Long, builder: Builder) {
    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sec)
}

fun setIso(iso: Int, builder: Builder) {
    builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
}

/**
 * 设置闪光灯模式。注意闪光灯会涉及到ae，因此它和setAE是冲突的。
 * */
fun setFlashMode(flashMode: FlashUtil.FlashMode, builder: Builder) {
    when (flashMode) {
        FlashUtil.FlashMode.OFF -> {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        FlashUtil.FlashMode.ON -> {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        FlashUtil.FlashMode.AUTO -> {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        FlashUtil.FlashMode.TORCH -> {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        }
    }
}

fun setWb(wbMode: Int, temp: Int?, tint: Int?, builder: Builder) {
    builder.set(CaptureRequest.CONTROL_AWB_MODE, wbMode)
    val isManual = wbMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
    if (isManual) {
        assert(temp != null)
        assert(tint != null)
        val rggbChannelVector = WbUtil.computeRggbChannelVector(temp!!, tint!!)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
    } else {
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
    }
}

fun setWb(wbMode: Int, temp: Int?, builder: Builder) {
    builder.set(CaptureRequest.CONTROL_AWB_MODE, wbMode)
    val isManual = wbMode == CameraCharacteristics.CONTROL_AWB_MODE_OFF
    if (isManual) {
        assert(temp != null)
        val rggbChannelVector = WbUtil.computeRggbChannelVector(temp!!)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, WbUtil.previousCST)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector)
    } else {
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
    }
}



