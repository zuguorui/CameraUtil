package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.media.MediaCodecInfo
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zu.camerautil.databinding.ItemCodecBinding

class CodecListAdapter(val context: Context): RecyclerView.Adapter<CodecListViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    private val data = ArrayList<MediaCodecInfo>()

    var onItemClickListener: ((position: Int, codecInfo: MediaCodecInfo) -> Unit)? = null

    private val innerItemClickListener = View.OnClickListener {
        val position = it.tag as Int
        onItemClickListener?.invoke(position, data[position])
    }

    fun addData(data: Collection<MediaCodecInfo>) {
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

    fun clearData() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodecListViewHolder {
        val binding = ItemCodecBinding.inflate(layoutInflater, parent, false)
        binding.root.isClickable = true
        binding.root.setOnClickListener(innerItemClickListener)
        return CodecListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CodecListViewHolder, position: Int) {
        val info = data[position]
        holder.binding.apply {
            root.tag = position
            tvName.text = info.name
            tvUnderlyingName.text = if (Build.VERSION.SDK_INT >= 29) {
                info.canonicalName
            } else {
                "29版本以上支持"
            }

            if (Build.VERSION.SDK_INT >= 29) {
                tvOnlySoftware.text = "${info.isSoftwareOnly}"
                tvOnlySoftware.setTextColor(if (info.isSoftwareOnly) Color.GREEN else Color.RED)
            } else {
                tvOnlySoftware.text = "29版本以上支持"
                tvOnlySoftware.setTextColor(Color.RED)
            }

            if (Build.VERSION.SDK_INT >= 29) {
                tvHardwareAccelerated.text = "${info.isHardwareAccelerated}"
                tvHardwareAccelerated.setTextColor(if (info.isHardwareAccelerated) Color.GREEN else Color.RED)
            } else {
                tvHardwareAccelerated.text = "29版本以上支持"
                tvHardwareAccelerated.setTextColor(Color.RED)
            }

            if (Build.VERSION.SDK_INT >= 29) {
                tvProviderName.text = if (info.isVendor) "设备制造商" else "Android Platform"
            } else {
                tvProviderName.text = "29版本以上支持"
            }

            tvCodecType.text = if (info.isEncoder) "编码器" else "解码器"
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }
}

data class CodecListViewHolder(val binding: ItemCodecBinding): RecyclerView.ViewHolder(binding.root)