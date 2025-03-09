package com.example.swapstyleproject.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.swapstyleproject.databinding.ItemClothingBinding
import com.example.swapstyleproject.model.ClothingItem
import com.google.android.material.tabs.TabLayoutMediator
import android.view.View
import androidx.core.content.ContextCompat
import com.example.swapstyleproject.model.ItemStatus
import com.example.swapstyleproject.utilities.FavoriteButtonHelper
import android.widget.PopupMenu
import com.example.swapstyleproject.R

//Displays images with indicator (dots)
//Allows adding/removing from favorites
//Hides favorites button for user items

    class ClothingItemsAdapter(
    private val onFavoriteClick: (String, Boolean) -> Unit,
    private val onItemClick: ((ClothingItem) -> Unit)? = null,
    private val onItemDelete: ((ClothingItem) -> Unit)? = null,
    private val showStatus: Boolean = false
) : ListAdapter<ClothingItem, ClothingItemsAdapter.ClothingViewHolder>(ClothingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClothingViewHolder {
        val binding = ItemClothingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ClothingViewHolder(
            binding,
            onFavoriteClick,
            onItemClick,
            onItemDelete,
            showStatus)
    }

    override fun onBindViewHolder(holder: ClothingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ClothingViewHolder(
        private val binding: ItemClothingBinding,
        private val onFavoriteClick: (String, Boolean) -> Unit,
        private val onItemClick: ((ClothingItem) -> Unit)?,
        private val onItemDelete: ((ClothingItem) -> Unit)?,
        private val showStatus: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClothingItem) {
            val imageAdapter = ClothingImageAdapter(
                imageUrls = item.photos,
                onImageClick = { onItemClick?.invoke(item) }
            )

            binding.itemImageViewPager.adapter = imageAdapter

            // Setting the indicator for images (dots)
            TabLayoutMediator(binding.imageIndicator, binding.itemImageViewPager) { tab, _ ->
                tab.view.minimumWidth = 0  // Ensures that each tab does not expand beyond the appropriate size
            }.attach()

            // Show indicator only if there is more than one image
            binding.imageIndicator.visibility = if (item.photos.size > 1) View.VISIBLE else View.GONE

            binding.itemTitle.text = item.title

            if (showStatus && item.isOwnItem) {
                binding.statusChip.visibility = View.VISIBLE

                when (item.status) {
                    ItemStatus.AVAILABLE -> {
                        binding.statusChip.text = "Available"
                        binding.statusChip.setChipBackgroundColorResource(R.color.green)
                    }
                    ItemStatus.IN_PROCESS -> {
                        binding.statusChip.text = "In Process"
                        binding.statusChip.setChipBackgroundColorResource(R.color.tool_bar_color)
                    }
                    ItemStatus.SWAPPED -> {
                        binding.statusChip.text = "Swapped"
                        binding.statusChip.setChipBackgroundColorResource(R.color.heart_yellow)
                    }
                    ItemStatus.UNAVAILABLE -> {
                        binding.statusChip.text = "Unavailable"
                        binding.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
                    }
                }

                binding.statusChip.setTextColor(
                    ContextCompat.getColor(
                    binding.root.context,
                    if (item.status == ItemStatus.UNAVAILABLE) android.R.color.white else android.R.color.black
                ))

                binding.itemDetails.textSize = 10f
            } else {
                binding.statusChip.visibility = View.GONE
                binding.itemDetails.textSize = 12f
            }

            binding.itemDetails.text = "${item.brand} | ${item.size} | ${item.category}"

            // Setup favorite button using the helper
            FavoriteButtonHelper.setupFavoriteButton(
                button = binding.favoriteButton,
                itemId = item.id,
                isOwnItem = item.isOwnItem,
                initialFavoriteState = item.isFavorite,
                onFavoriteClick = onFavoriteClick
            )

            // Setting up a click on the item - both on the image and on the tab
            onItemClick?.let { listener ->
                binding.root.setOnClickListener { listener(item) }
                binding.itemImageViewPager.setOnClickListener { listener(item) }
                (binding.itemImageViewPager.parent as View).setOnClickListener { listener(item) }
            }

            // Add delete option for available items
            if (showStatus && item.isOwnItem && item.status == ItemStatus.AVAILABLE) {
                binding.moreOptionsButton.visibility = View.VISIBLE
                binding.moreOptionsButton.setOnClickListener { view ->
                    showPopupMenu(view, item)
                }
            } else {
                binding.moreOptionsButton.visibility = View.GONE
            }
        }

        // Show popup menu for delete option
        private fun showPopupMenu(view: View, item: ClothingItem) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menuInflater.inflate(R.menu.item_options_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_delete -> {
                        onItemDelete?.invoke(item)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }
}

private class ClothingDiffCallback : DiffUtil.ItemCallback<ClothingItem>() {
    override fun areItemsTheSame(oldItem: ClothingItem, newItem: ClothingItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ClothingItem, newItem: ClothingItem): Boolean {
        return oldItem == newItem
    }
}