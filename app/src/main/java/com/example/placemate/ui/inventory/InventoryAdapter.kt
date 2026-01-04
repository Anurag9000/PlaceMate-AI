package com.example.placemate.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.databinding.ItemInventoryBinding
import com.example.placemate.data.local.entities.LocationEntity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class InventoryAdapter(
    private val onItemClick: (ItemEntity) -> Unit,
    private val onFolderClick: (LocationEntity) -> Unit,
    private val onFolderLongClick: (LocationEntity) -> Unit
) : ListAdapter<ExplorerItem, RecyclerView.ViewHolder>(ExplorerDiffCallback()) {

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_FILE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ExplorerItem.Folder -> TYPE_FOLDER
            is ExplorerItem.File -> TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            val binding = ItemInventoryBinding.inflate(inflater, parent, false) 
            FolderViewHolder(binding)
        } else {
            val binding = ItemInventoryBinding.inflate(inflater, parent, false)
            FileViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ExplorerItem.Folder -> (holder as FolderViewHolder).bind(item)
            is ExplorerItem.File -> (holder as FileViewHolder).bind(item)
        }
    }

    inner class FolderViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExplorerItem.Folder) {
            binding.itemName.text = item.location.name
            binding.itemCategory.text = "Contains items..." // Placeholder count
            binding.itemStatus.text = "FOLDER"
            if (!item.location.photoUri.isNullOrEmpty()) {
                binding.itemImage.setImageURI(android.net.Uri.parse(item.location.photoUri))
            } else {
                binding.itemImage.setImageResource(android.R.drawable.ic_menu_more) // Folder icon placeholder
            }
            
            binding.root.setOnClickListener { onFolderClick(item.location) }
            binding.root.setOnLongClickListener {
                onFolderLongClick(item.location)
                true
            }
            
            // Visual tweaks for folder
            binding.root.setCardBackgroundColor(android.graphics.Color.parseColor("#F0F4F8")) // Light blue/gray
        }
    }

    inner class FileViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExplorerItem.File) {
            val entity = item.item
            binding.itemName.text = entity.name
            binding.itemCategory.text = entity.category
            binding.itemStatus.text = entity.status.name
             if (!entity.photoUri.isNullOrEmpty()) {
                binding.itemImage.setImageURI(android.net.Uri.parse(entity.photoUri))
            } else {
                binding.itemImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            binding.root.setCardBackgroundColor(android.graphics.Color.WHITE)
            binding.root.setOnClickListener { onItemClick(entity) }
        }
    }

    class ExplorerDiffCallback : DiffUtil.ItemCallback<ExplorerItem>() {
        override fun areItemsTheSame(oldItem: ExplorerItem, newItem: ExplorerItem): Boolean {
           return if (oldItem is ExplorerItem.Folder && newItem is ExplorerItem.Folder) {
               oldItem.location.id == newItem.location.id
           } else if (oldItem is ExplorerItem.File && newItem is ExplorerItem.File) {
               oldItem.item.id == newItem.item.id
           } else false
        }

        override fun areContentsTheSame(oldItem: ExplorerItem, newItem: ExplorerItem): Boolean {
            return oldItem == newItem
        }
    }
}
