package com.example.placemate.ui.locations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.databinding.ItemLocationBinding

class LocationAdapter(
    private val onItemClick: (LocationEntity) -> Unit
) : ListAdapter<LocationEntity, LocationAdapter.ViewHolder>(LocationDiffCallback()) {

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

        fun bind(item: LocationEntity) {
            binding.locationName.text = item.name
            binding.locationType.text = item.type.name
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    class LocationDiffCallback : DiffUtil.ItemCallback<LocationEntity>() {
        override fun areItemsTheSame(oldItem: LocationEntity, newItem: LocationEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LocationEntity, newItem: LocationEntity): Boolean {
            return oldItem == newItem
        }
    }
}
