package com.haunted421.clipbubdeep.clipboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.haunted421.clipbubdeep.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (ClipboardEntry) -> Unit,
    private val onItemLongClick: (ClipboardEntry) -> Unit
) : ListAdapter<ClipboardEntry, HistoryAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    inner class ViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        // Show up to 3 lines of preview
        holder.binding.tvEntryText.text = entry.text.take(300)
        holder.binding.tvTimestamp.text = dateFormat.format(entry.date)
        holder.binding.tvSourcePackage.text = entry.sourcePackage
            ?.substringAfterLast('.') ?: ""
        holder.itemView.setOnClickListener { onItemClick(entry) }
        holder.itemView.setOnLongClickListener { onItemLongClick(entry); true }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ClipboardEntry>() {
        override fun areItemsTheSame(a: ClipboardEntry, b: ClipboardEntry) = a.id == b.id
        override fun areContentsTheSame(a: ClipboardEntry, b: ClipboardEntry) = a == b
    }
}
