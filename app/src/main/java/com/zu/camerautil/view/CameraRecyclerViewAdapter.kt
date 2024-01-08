package com.zu.camerautil.view

import android.hardware.camera2.CameraCharacteristics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.databinding.ItemCameraFullBinding
import com.zu.camerautil.toFormattedString
import com.zu.camerautil.toFormattedText

class CameraRecyclerViewAdapter: RecyclerView.Adapter<CameraRecyclerViewAdapter.CameraHolder>() {

    private val data = ArrayList<CameraInfoWrapper>()

    private var inflater: LayoutInflater? = null

    var onItemClickListener: ((info: CameraInfoWrapper) -> Unit)? = null

    private val internalClickListener = View.OnClickListener {
        val position = it.tag as Int
        onItemClickListener?.invoke(data[position])
    }

    fun addData(infoList: Collection<CameraInfoWrapper>) {
        val begin = data.size
        data.addAll(infoList)
        //notifyItemRangeInserted(begin, infoList.size)
        notifyDataSetChanged()
    }

    fun clearData() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraHolder {
        val inflater = inflater ?: run {
            inflater = LayoutInflater.from(parent.context)
            inflater!!
        }

        val binding = ItemCameraFullBinding.inflate(inflater, parent, false)
        binding.root.setOnClickListener(internalClickListener)
        return CameraHolder(binding)
    }

    override fun onBindViewHolder(holder: CameraHolder, position: Int) {
        val info = data[position]!!
        val binding = holder.binding
        binding.root.tag = position
        binding.tvId.text = "${info.cameraID}"
        binding.tvFacing.text = when (info.lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
            CameraCharacteristics.LENS_FACING_BACK -> "后置"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置"
            else -> "未知"
        }
        binding.tvLogical.text = if (info.isLogical) "逻辑摄像头" else "物理摄像头"
        binding.tvQueryFromIdList.text = if (info.isInCameraIdList) "是" else "否"
        binding.tvFocal.text = info.focalArray.toFormattedText(2)

        if (info.isLogical) {
            binding.llLogicalId.visibility = View.GONE
            binding.llPhysicalId.visibility = View.VISIBLE
            binding.tvPhysicalId.text = info.logicalPhysicalIDs.toFormattedString()
        } else {
            binding.llPhysicalId.visibility = View.GONE
            if (info.logicalID != null) {
                binding.llLogicalId.visibility = View.VISIBLE
                binding.tvLogicalId.text = info.logicalID
            } else {
                binding.llLogicalId.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class CameraHolder(val binding: ItemCameraFullBinding): RecyclerView.ViewHolder(binding.root)
}