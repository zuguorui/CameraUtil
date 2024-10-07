package com.zu.camerautil

import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Range
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zu.camerautil.databinding.ActivityCodecDetailBinding
import com.zu.camerautil.databinding.ItemCameraSubInfoBinding
import com.zu.camerautil.util.dpToPx
import com.zu.camerautil.util.findCodecByName
import timber.log.Timber
import java.lang.StringBuilder
import java.lang.reflect.Field

class CodecDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodecDetailBinding
    private lateinit var codecInfo: MediaCodecInfo

    private val isNewerOrEqualSDK29 = Build.VERSION.SDK_INT >= 29
    private val warnOnlySupportSinceSDK29 = "仅在29及以上版本支持"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodecDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        intent.getStringExtra("codec_name")?.let { codecName ->
            findCodecByName(codecName)?.let {
                codecInfo = it
                initItems()
            } ?: run {
                Timber.e("don't find codec which name is {$codecName}")
            }
        } ?: run {
            Timber.e("codec name is null")
        }

    }

    private fun initItems() {
        binding.tvCodecName.text = codecInfo.name

        val canonicalName = if (isNewerOrEqualSDK29) codecInfo.canonicalName else warnOnlySupportSinceSDK29
        addItem("实现名称", canonicalName)

        val softwareOnly = if (isNewerOrEqualSDK29) {
            "${codecInfo.isSoftwareOnly}"
        } else {
            warnOnlySupportSinceSDK29
        }
        addItem("仅支持软件", softwareOnly)

        val hardwareAccelerated = if (isNewerOrEqualSDK29) {
            "${codecInfo.isHardwareAccelerated}"
        } else {
            warnOnlySupportSinceSDK29
        }
        addItem("支持硬件加速", hardwareAccelerated)

        val providerName = if (isNewerOrEqualSDK29) {
            if (codecInfo.isVendor) "设备制造商" else "Android platform"
        } else {
            warnOnlySupportSinceSDK29
        }
        addItem("提供方", providerName)

        val codecType = if (codecInfo.isEncoder) "编码器" else "解码器"
        addItem("Codec类型", codecType)

        val supportTypes = run {
            val sb = StringBuilder()
            for (i in codecInfo.supportedTypes.indices) {
                sb.append(codecInfo.supportedTypes[i])
                if (i < codecInfo.supportedTypes.size - 1) {
                    sb.append("\n")
                }
            }
            sb.toString()
        }
        addItem("支持的类型", supportTypes)

        codecInfo.supportedTypes.forEach { type ->
            addTitle("${type}属性")

            val capabilities = codecInfo.getCapabilitiesForType(type)

            val isVideoCodec = capabilities.mimeType.startsWith("video")
            val isAudioCodec = capabilities.mimeType.startsWith("audio")
            val isImageCodec = capabilities.mimeType.startsWith("image")

            addItem("mimeType", capabilities.mimeType)
            addItem("映射的MediaFormat.MIMETYPE_*", getMediaFormatMimeType(capabilities.mimeType) ?: "null")

            addItem("profileLevels", capabilities.profileLevels.toFormattedString("\n\n") {
                """
                        profile: ${getMediaCodecProfileName(capabilities.mimeType, it.profile) ?: it.profile}
                        level: ${getMediaCodecLevelName(capabilities.mimeType, it.level) ?: it.level}
                    """.trimIndent()
            })

            addItem("最大实例数量", capabilities.maxSupportedInstances)

            val supportedFeatures = mediaCodecFeatureMap.keys.filter {
                capabilities.isFeatureSupported(it)
            }
            addItem("支持的特性", supportedFeatures.toFormattedString())

            val requiredFeatures = mediaCodecFeatureMap.keys.filter {
                capabilities.isFeatureRequired(it)
            }
            addItem("要求的特性", requiredFeatures.toFormattedString())

            capabilities.encoderCapabilities?.let {
                addItem("复杂度范围", it.complexityRange)

                if (Build.VERSION.SDK_INT >= 28) {
                    addItem("质量范围", it.qualityRange)
                }

                var bitrateMode = ""
                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
                    bitrateMode += "\n固定质量"
                }
                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                    bitrateMode += "\n可变码率"
                }
                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                    bitrateMode += "\n固定码率"
                }
                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD)) {
                    bitrateMode += "\n固定码率且支持丢帧"
                }
                addItem("码率模式", bitrateMode.substring(1))
            }




            if (isAudioCodec) {
                capabilities.audioCapabilities.let {
                    addItem("码率范围", it.bitrateRange.toString())
                    if (Build.VERSION.SDK_INT >= 31) {
                        val rangeStr = it.inputChannelCountRanges.toFormattedString()
                        addItem("声道数量范围", rangeStr)
                    }

                    addItem("采样率范围", it.supportedSampleRateRanges.toFormattedString())
                    addItem("采样率", it.supportedSampleRates?.toFormattedString())
                }
            }

            if (isVideoCodec) {
                addItem("颜色格式", reflectMediaCodecColorFormatName(capabilities.colorFormats).values.toFormattedString())

                capabilities.videoCapabilities.let {
                    addItem("码率范围", it.bitrateRange)
                    addItem("帧率范围", it.supportedFrameRates)
                    addItem("高度对齐基数", it.heightAlignment)
                    addItem("宽度对齐基数", it.widthAlignment)

                    addItem("支持的宽度范围", it.supportedWidths)
                    addItem("支持的高度范围", it.supportedHeights)
                    addItem("支持的帧率范围", it.supportedFrameRates)

                    if (isNewerOrEqualSDK29) {
                        addItem("支持的性能点", it.supportedPerformancePoints?.toFormattedString())
                    }
//                    val supportedSizeFps = ArrayList<Triple<Int, Int, Range<Double>>>()
//                    var width = it.supportedWidths.lower
//                    while (width <= it.supportedWidths.upper) {
//                        val supportedHeightRange = it.getSupportedHeightsFor(width)
//                        var height = supportedHeightRange.lower
//                        while (height <= supportedHeightRange.upper) {
//                            if (it.isSizeSupported(width, height)) {
//                                supportedSizeFps.add(Triple(width, height, it.getSupportedFrameRatesFor(width, height)))
//                            }
//                            height += it.heightAlignment
//                        }
//                        width += it.widthAlignment
//                    }
//                    Timber.d("supportedSizeFps.size = ${supportedSizeFps.size}")
//                    addItem("支持的尺寸及帧率范围", supportedSizeFps.toFormattedString("\n") { triple ->
//                        "${triple.first}x${triple.second}, ${String.format("%.2f", triple.third)}fps"
//                    })
                }
            }

        }
    }

    private var itemIndex = 0
    private fun addItem(name: String, content: Any?) {
        val itemBinding = ItemCameraSubInfoBinding.inflate(layoutInflater)
        itemBinding.tvName.text = name
        itemBinding.tvValue.text = content?.toString() ?: "null"
        val color = if (itemIndex % 2 == 0) R.color.blue_dark else R.color.blue_light
        itemBinding.root.setBackgroundColor(getColor(color))
        binding.llItems.addView(itemBinding.root)
        itemIndex++
    }

    private fun addTitle(title: String) {
        val textView = TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
            text = title
            val padding = dpToPx(this@CodecDetailActivity, 10f).toInt()
            setPadding(0, padding, 0, padding)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            val color = if (itemIndex % 2 == 0) R.color.blue_dark else R.color.blue_light
            setBackgroundColor(getColor(color))
        }
        binding.llItems.addView(textView)
        itemIndex++
    }

    private fun reflectMediaCodecColorFormatName(formatInt: IntArray): Map<Int, String> {
        val map = HashMap<Int, String>()
        val clazz = MediaCodecInfo.CodecCapabilities::class.java
        clazz.declaredFields.forEach {
            if (it.name.startsWith("COLOR_Format")) {
                val value = it.get(null) as Int
                if (formatInt.contains(value)) {
                    map[value] = it.name
                }
            }
        }
        return map
    }

    private val codecProfileLevelFields = ArrayList<Field>().apply {
        val clazz = MediaCodecInfo.CodecProfileLevel::class.java
        this.addAll(clazz.declaredFields)
    }

    /**
     * 根据mimeType及profile，从[MediaCodecInfo.CodecProfileLevel]中获取对应的属性名。
     * */
    private fun getMediaCodecProfileName(mimeType: String, profile: Int): String? {
        // 先获取其在Android API内部的名称
        val mediaFormatMimeType = getMediaFormatMimeType(mimeType) ?: return null
        var format = ""
        mediaFormatMimeType.split("_").drop(2).forEach {
            format += it
        }
        Timber.d("getProfileName: format = $format")
        // aac是AACObject*
        val profileName = if (format.lowercase() == "aac") {
            "Object"
        } else {
            "Profile"
        }
        return codecProfileLevelFields.find {
            //Timber.d("find profile ${it.name}")
            it.name.lowercase().startsWith(format.lowercase()) && it.name.contains(profileName) && (it.get(null) as Int) == profile
        }?.name
    }

    /**
     * 根据mimeType及profile，从[MediaCodecInfo.CodecProfileLevel]中获取对应的属性名。
     * */
    private fun getMediaCodecLevelName(mimeType: String, level: Int): String? {
        val mediaFormatMimeType = getMediaFormatMimeType(mimeType) ?: return null
        var format = ""
        mediaFormatMimeType.split("_").drop(2).forEach {
            format += it
        }
        Timber.d("getLevelName: format = $format")
        return codecProfileLevelFields.find {
            //Timber.d("find level ${it.name}")
            it.name.lowercase().startsWith(format.lowercase()) && it.name.contains("Level") && (it.get(null) as Int) == level
        }?.name
    }

    private val mediaFormatMimeTypeMap = HashMap<String, String>().apply {
        val clazz = MediaFormat::class.java
        clazz.declaredFields.forEach {
            if (it.name.startsWith("MIMETYPE")) {
                val value = it.get(null) as String
                this[value] = it.name
            }
        }
    }

    private fun getMediaFormatMimeType(mimeType: String): String? {
        return mediaFormatMimeTypeMap[mimeType]
    }

    private val mediaCodecFeatureMap = HashMap<String, String>().apply {
        val clazz = MediaCodecInfo.CodecCapabilities::class.java
        clazz.declaredFields.forEach {
            if (it.name.startsWith("FEATURE_")) {
                val value = it.get(null) as String
                this[value] = it.name
            }
        }
    }

}