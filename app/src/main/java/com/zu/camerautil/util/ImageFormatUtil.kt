package com.zu.camerautil.util

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.provider.ContactsContract.CommonDataKinds.Im

val pixelFormatNameMap = HashMap<Int, String>().apply {
    this[PixelFormat.UNKNOWN] = "UNKNOWN"
    this[PixelFormat.OPAQUE] = "OPAQUE"
    this[PixelFormat.RGBA_1010102] = "RGBA_1010102"
    this[PixelFormat.RGBA_F16] = "RGBA_F16"
    this[PixelFormat.RGBA_8888] = "RGBA_8888"
    this[PixelFormat.RGBX_8888] = "RGBX_8888"
    this[PixelFormat.RGB_565] = "RGB_565"
    this[PixelFormat.RGB_888] = "RGB_888"
    this[PixelFormat.TRANSLUCENT] = "TRANSLUCENT"
    this[PixelFormat.TRANSPARENT] = "TRANSPARENT"
}

val imageFormatNameMap = HashMap<Int, String>().apply {
    this[ImageFormat.UNKNOWN] = "UNKNOWN"
    this[ImageFormat.RGB_565] = "RGB_565"
    this[ImageFormat.YV12] = "YV12"
    this[ImageFormat.Y8] = "Y8"
    this[ImageFormat.YCBCR_P010] = "YCBCR_P010"
    this[ImageFormat.NV16] = "NV16"
    this[ImageFormat.NV21] = "NV21"
    this[ImageFormat.YUY2] = "YUY2"
    this[ImageFormat.JPEG] = "JPEG"
    this[ImageFormat.DEPTH_JPEG] = "DEPTH_JPEG"
    this[ImageFormat.JPEG_R] = "JPEG_R"
    this[ImageFormat.YUV_420_888] = "YUV_420_888"
    this[ImageFormat.YUV_422_888] = "YUV_422_888"
    this[ImageFormat.YUV_444_888] = "YUV_444_888"
    this[ImageFormat.FLEX_RGB_888] = "FLEX_RGB_888"
    this[ImageFormat.FLEX_RGBA_8888] = "FLEX_RGBA_8888"
    this[ImageFormat.RAW_SENSOR] = "RAW_SENSOR"
    this[ImageFormat.RAW_PRIVATE] = "RAW_PRIVATE"
    this[ImageFormat.RAW10] = "RAW10"
    this[ImageFormat.RAW12] = "RAW12"
    this[ImageFormat.DEPTH16] = "DEPTH16"
    this[ImageFormat.DEPTH_POINT_CLOUD] = "DEPTH_POINT_CLOUD"
    this[ImageFormat.PRIVATE] = "PRIVATE"
    this[ImageFormat.HEIC] = "HEIC"
}

fun getImageFormatName(format: Int): String {
    var pixelFormatName = pixelFormatNameMap[format]?.let {
        "PixelFormat.$it"
    }
    var imageFormatName = imageFormatNameMap[format]?.let {
        "ImageFormat.$it"
    }

    if (pixelFormatName != null && imageFormatName != null) {
        return "$pixelFormatName/$imageFormatName"
    } else if (pixelFormatName != null) {
        return "$pixelFormatName"
    } else if (imageFormatName != null) {
        return "$imageFormatName"
    } else {
        return "unknown"
    }
}