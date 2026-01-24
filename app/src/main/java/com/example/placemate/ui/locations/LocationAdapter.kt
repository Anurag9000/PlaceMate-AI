package com.example.placemate.ui.locations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.placemate.data.local.dao.LocationWithCount
import com.example.placemate.databinding.ItemLocationBinding

class LocationAdapter(
    private val onItemClick: (LocationWithCount) -> Unit,
    private val onItemLongClick: (LocationWithCount) -> Unit
) : ListAdapter<LocationWithCount, LocationAdapter.ViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLocationBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemLocationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LocationWithCount) {
            binding.locationName.text = item.location.name
            binding.locationType.text = item.location.type.name
            binding.itemCount.text = "${item.itemCount} items"
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    class LocationDiffCallback : DiffUtil.ItemCallback<LocationWithCount>() {
        override fun areItemsTheSame(oldItem: LocationWithCount, newItem: LocationWithCount): Boolean {
            return oldItem.location.id == newItem.location.id
        }

        override fun areContentsTheSame(oldItem: LocationWithCount, newItem: LocationWithCount): Boolean {
            return oldItem == newItem
        }
    }
}
