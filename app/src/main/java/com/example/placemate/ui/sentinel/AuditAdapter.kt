package com.example.placemate.ui.sentinel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.placemate.R
import com.example.placemate.databinding.ItemInventoryBinding

class AuditAdapter : ListAdapter<AuditItem, AuditAdapter.AuditViewHolder>(AuditDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuditViewHolder {
        val binding = ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AuditViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AuditViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AuditViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AuditItem) {
            binding.itemName.text = item.name
            
            val statusText = when (item.status) {
                AuditStatus.MATCHED -> "MATCHED"
                AuditStatus.MISSING -> "MISSING"
                AuditStatus.NEW -> "NEW/MOVED"
            }
            
            val statusColor = when (item.status) {
                AuditStatus.MATCHED -> binding.root.context.getColor(android.R.color.holo_green_dark)
                AuditStatus.MISSING -> binding.root.context.getColor(android.R.color.holo_red_dark)
                AuditStatus.NEW -> binding.root.context.getColor(android.R.color.holo_orange_dark)
            }
            
            binding.itemStatus.text = statusText
            binding.itemStatus.setTextColor(statusColor)
            
            // Re-using the inventory item layout but adapting it for audit
            if (item.photoUri != null) {
                binding.itemImage.setImageURI(android.net.Uri.parse(item.photoUri))
            } else {
                val icon = when (item.status) {
                    AuditStatus.MATCHED -> android.R.drawable.ic_input_add // Or a checkmark
                    AuditStatus.MISSING -> android.R.drawable.ic_delete
                    AuditStatus.NEW -> android.R.drawable.ic_menu_help
                }
                binding.itemImage.setImageResource(icon)
            }
            
            binding.root.alpha = if (item.status == AuditStatus.MISSING) 0.6f else 1.0f
        }
    }

    class AuditDiffCallback : DiffUtil.ItemCallback<AuditItem>() {
        override fun areItemsTheSame(oldItem: AuditItem, newItem: AuditItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AuditItem, newItem: AuditItem): Boolean = oldItem == newItem
    }
}
