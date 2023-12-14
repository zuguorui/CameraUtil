package com.zu.camerautil.view

import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SpinnerAdapter
import android.widget.TextView
import com.zu.camerautil.R
import com.zu.camerautil.bean.CameraInfoWrapper

class CameraAdapter: BaseAdapter() {

    private val data = ArrayList<CameraInfoWrapper>()

    private var layoutInflater: LayoutInflater? = null

    fun setData(data: Collection<CameraInfoWrapper>) {
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

    fun clearData() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun getItem(position: Int): Any {
        return data[position]!!
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val view = convertView ?: kotlin.run {
            val inflater = layoutInflater ?: kotlin.run {
                layoutInflater = LayoutInflater.from(parent.context)
                layoutInflater!!
            }
            inflater.inflate(R.layout.item_camera, parent, false)
        }

        val tv = view.findViewById<TextView>(R.id.tv)

        val info = data[position]
        val facing = if (info.lensFacing == CameraCharacteristics.LENS_FACING_BACK) "back" else "front"
        val logical = if (info.isLogical) "logical" else "physical"
        val focal = if (info.focalArray.isNotEmpty()) String.format("%.1f", info.focalArray[0]) else "_"
        val str = "Camera${info.cameraID}_${facing}_${logical}_focal(${focal})"
        tv.text = str
        if (info.isPresentByCameraManager) {
            tv.setTextColor(Color.GREEN)
        } else {
            tv.setTextColor(Color.RED)
        }
        return view
    }
}