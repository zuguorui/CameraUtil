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
import com.zu.camerautil.bean.AdjustUiElement
import com.zu.camerautil.bean.SelectionParam
import com.zu.camerautil.util.dpToPx

class SelectionParamPopupWindow<T>: EasyLayoutPopupWindow {

    var onParamSelectedListener: ((T) -> Unit)? = null

    private var recyclerView: RecyclerView

    private var adapter: Adapter = Adapter()

    private val data = ArrayList<AdjustUiElement>()

    var param: SelectionParam<T>? = null
        set(value) {
            field = value
            data.clear()
            field?.let {
                for (v in it.values) {
                    val uiElement = it.valueToUiElement(v)
                    data.add(uiElement)
                }
            }
            adapter.notifyDataSetChanged()
        }

    private var _current: T? = null
    var current: T?
        get() = _current
        set(value) {
            _current = value
        }


    private val internalClickListener = object : View.OnClickListener {
        override fun onClick(v: View) {
            param?.let {
                val position = v.tag as Int
                onParamSelectedListener?.invoke(it.values[position])
                dismiss()
            }

        }
    }

    constructor(context: Context): super(context) {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.WHITE))

        recyclerView = RecyclerView(context).apply {
            this.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            //this.layoutParams = ViewGroup.LayoutParams(dpToPx(context, 400f).toInt(), dpToPx(context, 800f).toInt())
            this.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            this.adapter = this@SelectionParamPopupWindow.adapter
        }
        contentView = recyclerView
    }

    fun notifyDataChanged() {
        data.clear()
        param?.let {
            for (v in it.values) {
                val uiElement = it.valueToUiElement(v)
                data.add(uiElement)
            }
        }
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

}
