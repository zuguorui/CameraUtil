package com.zu.camerautil.camera

import android.hardware.camera2.CameraCharacteristics

/**
 * @author zuguorui
 * @date 2024/1/9
 * @description
 */

val WB_MODE_NAME_MAP = HashMap<Int, String>().apply {
    put(CameraCharacteristics.CONTROL_AWB_MODE_AUTO, "自动")
    put(CameraCharacteristics.CONTROL_AWB_MODE_OFF, "关闭")
    put(CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT, "白炽灯")
    put(CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT, "荧光灯")
    put(CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT, "暖灯")
    put(CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT, "日光")
    put(CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "阴天")
    put(CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT, "黄昏")
    put(CameraCharacteristics.CONTROL_AWB_MODE_SHADE, "阴影")
}

fun getWbModeName(wbMode: Int): String? = WB_MODE_NAME_MAP[wbMode]
