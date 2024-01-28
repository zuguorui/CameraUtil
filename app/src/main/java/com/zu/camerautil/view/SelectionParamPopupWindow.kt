package com.zu.camerautil.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zu.camerautil.util.dpToPx

class SelectionParamPopupWindow: EasyLayoutPopupWindow {

//    val context: Context

    var onItemClickListener: ((Param) -> Unit)? = null

    private var recyclerView: RecyclerView

    private var adapter: Adapter = Adapter()

    private val data = ArrayList<Param>()

    private val internalClickListener = object : View.OnClickListener {
        override fun onClick(v: View) {
            val position = v.tag as Int
            onItemClickListener?.invoke(data[position]!!)
        }
    }

    constructor(context: Context): super(context) {
//        this.context = context
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.WHITE))

        recyclerView = RecyclerView(context).apply {
            this.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            //this.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            //this.layoutParams = ViewGroup.LayoutParams(dpToPx(context, 400f).toInt(), dpToPx(context, 800f).toInt())
            this.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            this.adapter = this@SelectionParamPopupWindow.adapter
        }
        contentView = recyclerView
    }


    fun setData(dataList: List<Param>) {
        data.clear()
        data.addAll(dataList)
        adapter.notifyDataSetChanged()
    }

    fun clearData() {
        data.clear()
        adapter.notifyDataSetChanged()
    }


    private inner class Adapter: RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(context).apply {
                this.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val padding = dpToPx(context, 10f).toInt()
                this.setPadding(padding, padding, padding, padding)
                this.setOnClickListener(internalClickListener)
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val param = data[position]
            val textView = holder.itemView as TextView
            textView.text = param.text
            textView.setTextColor(param.textColor)
            textView.tag = position
        }

        override fun getItemCount(): Int {
            return data.size
        }
    }

    private inner class ViewHolder(view: View): RecyclerView.ViewHolder(view)

    data class Param(
        val text: String,
        val id: Int,
        val textColor: Int = Color.BLACK,
        val background: Int = Color.WHITE
    )
}
