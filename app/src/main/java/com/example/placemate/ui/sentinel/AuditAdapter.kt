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
            
            val context = binding.root.context
            val statusText = when (item.status) {
                AuditStatus.MATCHED -> context.getString(R.string.audit_status_matched)
                AuditStatus.MISSING -> context.getString(R.string.audit_status_missing)
                AuditStatus.NEW -> context.getString(R.string.audit_status_new)
            }
            
            val statusColor = when (item.status) {
                AuditStatus.MATCHED -> context.getColor(R.color.success)
                AuditStatus.MISSING -> context.getColor(R.color.error)
                AuditStatus.NEW -> context.getColor(R.color.warning)
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
