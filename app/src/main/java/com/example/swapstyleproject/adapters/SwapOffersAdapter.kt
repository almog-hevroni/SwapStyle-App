package com.example.swapstyleproject.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.swapstyleproject.R
import com.example.swapstyleproject.databinding.ItemSwapOfferBinding
import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.User
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import com.example.swapstyleproject.data.repository.item.ItemRepository

//This adapter displays a list of swap offers with user details, item information,
//meeting location, and selected time slot
//It also handles the selection of offers through radio buttons.
class SwapOffersAdapter(
    private val onOfferSelected: (SwapOffer) -> Unit,
    private val onOfferedItemClick: (String) -> Unit
) : ListAdapter<SwapOffer, SwapOffersAdapter.SwapOfferViewHolder>(SwapOfferDiffCallback()) {

    private val repository = FirebaseRepository.getInstance()
    private val userRepository = repository.userRepository
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var selectedOfferId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwapOfferViewHolder {
        val binding = ItemSwapOfferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SwapOfferViewHolder(binding,
            onRadioClick = { position ->
                val offer = getItem(position)
                if (selectedOfferId != offer.swapId) {
                    val previousSelectedPosition = currentList.indexOfFirst { it.swapId == selectedOfferId }
                    selectedOfferId = offer.swapId

                    if (previousSelectedPosition != -1) {
                        notifyItemChanged(previousSelectedPosition)
                    }
                    notifyItemChanged(position)

                    onOfferSelected(offer)
                }
            },
            onOfferedItemClick = onOfferedItemClick,
            coroutineScope = coroutineScope,
            itemRepository = repository.itemRepository
        )
    }

    override fun onBindViewHolder(holder: SwapOfferViewHolder, position: Int) {
        val offer = getItem(position)
        holder.bind(offer, offer.swapId == selectedOfferId)

        coroutineScope.launch {
            try {
                val userResult = userRepository.getUserProfile(offer.interestedUserId)
                userResult.onSuccess { user ->
                    withContext(Dispatchers.Main) {
                        holder.bindUserDetails(user)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class SwapOfferViewHolder(
        private val binding: ItemSwapOfferBinding,
        private val onRadioClick: (Int) -> Unit,
        private val onOfferedItemClick: (String) -> Unit,
        private val coroutineScope: CoroutineScope,
        private val itemRepository: ItemRepository
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.offerRadioButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRadioClick(position)
                }
            }

            binding.offeredItemImage.setOnClickListener {
                val offer = it.tag as? SwapOffer
                offer?.offeredItemId?.let { itemId ->
                    onOfferedItemClick(itemId)
                }
            }
            binding.offeredItemTitle.setOnClickListener {
                val offer = it.tag as? SwapOffer
                offer?.offeredItemId?.let { itemId ->
                    onOfferedItemClick(itemId)
                }
            }
        }

        fun bind(offer: SwapOffer, isSelected: Boolean) {
            binding.offeredItemImage.tag = offer
            binding.offeredItemTitle.tag = offer

            binding.offerRadioButton.isChecked = isSelected

            binding.locationTextView.text = offer.selectedLocation.name
            binding.locationAddressTextView.text = offer.selectedLocation.address
            binding.timeSlotTextView.text = offer.selectedTimeSlot

            binding.offeredItemTitle.text = offer.offeredItemTitle
            binding.offeredItemDetails.text = "View item details"

            if (offer.offeredItemPhotoUrls.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .load(offer.offeredItemPhotoUrls[0])
                    .placeholder(R.drawable.ic_add_photo)
                    .error(R.drawable.ic_add_photo)
                    .centerCrop()
                    .into(binding.offeredItemImage)
            } else {
                binding.offeredItemImage.setImageResource(R.drawable.ic_add_photo)
            }

            // Checking if the item offered is still available
            if (!offer.offeredItemId.isNullOrEmpty()) {
                coroutineScope.launch {
                    try {
                        itemRepository.isItemAvailableForSwap(offer.offeredItemId)
                            .onSuccess { isAvailable ->
                                if (!isAvailable) {
                                    // Item is no longer available - updating the UI
                                    binding.offerRadioButton.isEnabled = false
                                    binding.root.alpha = 0.5f
                                    binding.statusChip.visibility = View.VISIBLE
                                    binding.statusChip.text = "Item no longer available"
                                    binding.statusChip.chipBackgroundColor = ColorStateList.valueOf(
                                        ContextCompat.getColor(binding.root.context, R.color.red)
                                    )
                                } else {
                                    // The item is available - allows selection
                                    binding.offerRadioButton.isEnabled = true
                                    binding.root.alpha = 1.0f
                                    binding.statusChip.visibility = View.GONE
                                }
                            }
                    } catch (e: Exception) {
                        binding.offerRadioButton.isEnabled = true
                        binding.root.alpha = 1.0f
                        binding.statusChip.visibility = View.GONE
                    }
                }
            } else {
                binding.offerRadioButton.isEnabled = true
                binding.root.alpha = 1.0f
                binding.statusChip.visibility = View.GONE
            }
        }

        fun bindUserDetails(user: User) {
            binding.usernameTextView.text = user.username
            binding.swapCountTextView.text = "${user.swapCount} Swaps"

            Glide.with(binding.root.context)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(binding.userProfileImage)
        }
    }


    private class SwapOfferDiffCallback : DiffUtil.ItemCallback<SwapOffer>() {
        override fun areItemsTheSame(oldItem: SwapOffer, newItem: SwapOffer): Boolean {
            return oldItem.swapId == newItem.swapId
        }

        override fun areContentsTheSame(oldItem: SwapOffer, newItem: SwapOffer): Boolean {
            return oldItem == newItem
        }
    }
}