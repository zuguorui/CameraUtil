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
import com.zu.camerautil.bean.SelectionParam
import com.zu.camerautil.bean.UiElement
import com.zu.camerautil.bean.ValuesListener
import com.zu.camerautil.util.dpToPx
import timber.log.Timber

class SelectionParamPopupWindow: EasyLayoutPopupWindow {

    private var recyclerView: RecyclerView

    private var adapter: Adapter = Adapter()

    private val data = ArrayList<UiElement>()

    private val valuesListener: ValuesListener<Any> = {
        Timber.d("values changed: $param")
        onValuesChanged()
    }

    var param: SelectionParam<Any>? = null
        set(value) {
            Timber.d("param setter, field = $field, value = $value")
            field?.removeOnValuesChangedListener(valuesListener)
            value?.addOnValuesChangedListener(valuesListener)
            val diff = field != value
            field = value
            if (diff) {
                onValuesChanged()
            }
        }



    private val internalClickListener = object : View.OnClickListener {
        override fun onClick(v: View) {
            param?.let {
                val position = v.tag as Int
                val element = data[position]
                it.value = it.selectionElementToValue(element)
                dismiss()
            }
        }
    }

    constructor(context: Context): super(context) {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.WHITE))

        recyclerView = RecyclerView(context).apply {
            this.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            this.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            this.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            this.adapter = this@SelectionParamPopupWindow.adapter
        }
        contentView = recyclerView
    }

    private fun onValuesChanged() {
        Timber.d("onValuesChanged")
        data.clear()
        param?.let {
            for (v in it.values) {
                val uiElement = it.valueToSelectionElement(v)
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
