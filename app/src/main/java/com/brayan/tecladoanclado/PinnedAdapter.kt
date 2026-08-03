package com.brayan.tecladoanclado

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class PinnedAdapter(
    private var items: MutableList<ClipboardItem>,
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: (ClipboardItem, Int) -> Unit
) : RecyclerView.Adapter<PinnedAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.tvPinnedText)
        val pinIcon: TextView = view.findViewById(R.id.tvPinIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pinned_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item.text
        holder.pinIcon.visibility = if (item.isPinned) View.VISIBLE else View.GONE

        val handler = Handler(Looper.getMainLooper())
        var isLongPress = false

        val longPressRunnable = Runnable {
            if (isLongPress) {
                isLongPress = false
                onItemLongClick(item, holder.adapterPosition)
            }
        }

        holder.itemView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPress = true
                    handler.postDelayed(longPressRunnable, 2000) // Exactamente 2 segundos
                }
                MotionEvent.ACTION_UP -> {
                    if (isLongPress) {
                        isLongPress = false
                        handler.removeCallbacks(longPressRunnable)
                        onItemClick(item.text)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    isLongPress = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: MutableList<ClipboardItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
