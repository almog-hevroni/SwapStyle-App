package com.example.swapstyleproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.swapstyleproject.R
import com.example.swapstyleproject.model.ClothingItem


//This adapter manages a list of clothing items that the user can select to offer in a swap.
//It handles displaying each item and tracking which one is currently selected.
class ItemSelectionAdapter(
    private val onItemClick: (ClothingItem) -> Unit
) : ListAdapter<ClothingItem, ItemSelectionAdapter.ItemViewHolder>(ItemDiffCallback()) {

    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selection_for_swap, parent, false)
        return ItemViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    fun getSelectedItem(): ClothingItem? {
        return if (selectedPosition != RecyclerView.NO_POSITION) getItem(selectedPosition) else null
    }

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position

        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition)
        }

        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position)
        }
    }

    inner class ItemViewHolder(
        itemView: View,
        private val onItemClick: (ClothingItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val itemImage: ImageView = itemView.findViewById(R.id.itemImage)
        private val itemTitle: TextView = itemView.findViewById(R.id.itemTitle)
        private val itemDetails: TextView = itemView.findViewById(R.id.itemDetails)
        private val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    setSelectedPosition(position)
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: ClothingItem, isSelected: Boolean) {
            itemTitle.text = item.title
            itemDetails.text = "${item.brand} | ${item.size} | ${item.category}"

            // Load the first image
            if (item.photos.isNotEmpty()) {
                Glide.with(itemImage.context)
                    .load(item.photos[0])
                    .placeholder(R.drawable.ic_add_photo)
                    .error(R.drawable.ic_add_photo)
                    .centerCrop()
                    .into(itemImage)
            } else {
                itemImage.setImageResource(R.drawable.ic_add_photo)
            }

            // Update selection state
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.selected_item_background
                else R.drawable.selectable_item_background
            )
        }
    }

    private class ItemDiffCallback : DiffUtil.ItemCallback<ClothingItem>() {
        override fun areItemsTheSame(oldItem: ClothingItem, newItem: ClothingItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClothingItem, newItem: ClothingItem): Boolean {
            return oldItem == newItem
        }
    }
}