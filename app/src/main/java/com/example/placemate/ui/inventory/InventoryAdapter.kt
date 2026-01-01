package com.example.placemate.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.databinding.ItemInventoryBinding

class InventoryAdapter(
    private val onItemClick: (ItemEntity) -> Unit
) : ListAdapter<ItemEntity, InventoryAdapter.ViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInventoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemInventoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemEntity) {
            binding.itemName.text = item.name
            binding.itemCategory.text = item.category
            binding.itemStatus.text = item.status.name
            // Photo loading would go here (e.g., using Glide or Coil)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<ItemEntity>() {
        override fun areItemsTheSame(oldItem: ItemEntity, newItem: ItemEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ItemEntity, newItem: ItemEntity): Boolean {
            return oldItem == newItem
        }
    }
}
