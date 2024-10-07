package com.zu.camerautil.util

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.Field

@IntDef(
    MediaCodecList.ALL_CODECS,
    MediaCodecList.REGULAR_CODECS
)
@Retention(AnnotationRetention.SOURCE)
annotation class CodecType

fun listAllCodec(): ArrayList<MediaCodecInfo> {
    return listCodec(MediaCodecList.ALL_CODECS)
}


fun listRegularCodec(): ArrayList<MediaCodecInfo> {
    return listCodec(MediaCodecList.REGULAR_CODECS)
}

fun listCodec(@CodecType type: Int): ArrayList<MediaCodecInfo> {
    var codecList = MediaCodecList(type)
    var result = ArrayList<MediaCodecInfo>()
    result.addAll(codecList.codecInfos)
    return result
}


fun findCodecByName(name: String): MediaCodecInfo? {
    return listCodec(MediaCodecList.ALL_CODECS).find {
        it.name == name
    }
}



