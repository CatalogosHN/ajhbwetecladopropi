package com.brayan.tecladoanclado

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class PinnedAdapter(
    private var items: MutableList<String>,
    private val isEditable: Boolean,
    private val onItemClick: (String) -> Unit,
    private val onItemsChanged: () -> Unit
) : RecyclerView.Adapter<PinnedAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.tvPinnedText)
        val deleteIcon: ImageView = view.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pinned_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = items[position]
        holder.textView.text = text
        
        if (isEditable) {
            holder.deleteIcon.visibility = View.VISIBLE
            holder.deleteIcon.setOnClickListener {
                items.removeAt(position)
                notifyItemRemoved(position)
                onItemsChanged()
            }
        } else {
            holder.deleteIcon.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(text)
        }
    }

    override fun getItemCount() = items.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onItemsChanged()
    }

    fun updateData(newItems: MutableList<String>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
