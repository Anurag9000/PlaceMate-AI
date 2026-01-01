package com.example.placemate.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.databinding.ItemInventoryBinding

data class ItemUiModel(
    val item: ItemEntity,
    val count: Int
)

class InventoryAdapter(
    private val onItemClick: (ItemEntity) -> Unit
) : ListAdapter<ItemUiModel, InventoryAdapter.ViewHolder>(ItemDiffCallback()) {

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

        fun bind(uiModel: ItemUiModel) {
            val item = uiModel.item
            binding.itemName.text = if (uiModel.count > 1) "${item.name} (x${uiModel.count})" else item.name
            binding.itemCategory.text = item.category
            binding.itemStatus.text = item.status.name
            // Photo loading would go here
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<ItemUiModel>() {
        override fun areItemsTheSame(oldItem: ItemUiModel, newItem: ItemUiModel): Boolean {
            return oldItem.item.id == newItem.item.id
        }

        override fun areContentsTheSame(oldItem: ItemUiModel, newItem: ItemUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
