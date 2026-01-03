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
    val count: Int,
    val locationPath: String
)

class InventoryAdapter(
    private val onItemClick: (ItemEntity) -> Unit
) : ListAdapter<ItemUiModel, InventoryAdapter.ViewHolder>(ItemDiffCallback()) {

    // ... onCreateViewHolder ...

    inner class ViewHolder(private val binding: ItemInventoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(uiModel: ItemUiModel) {
            val item = uiModel.item
            binding.itemName.text = if (uiModel.count > 1) "${item.name} (x${uiModel.count})" else item.name
            
            // Show location path if available, otherwise category
            if (uiModel.locationPath.isNotEmpty()) {
                binding.itemCategory.text = "${uiModel.locationPath} • ${item.category}"
            } else {
                binding.itemCategory.text = item.category
            }
            
            binding.itemStatus.text = item.status.name
            
            if (!item.photoUri.isNullOrEmpty()) {
                binding.itemImage.setImageURI(android.net.Uri.parse(item.photoUri))
            } else {
                binding.itemImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }

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
