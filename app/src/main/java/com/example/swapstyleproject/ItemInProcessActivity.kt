package com.example.swapstyleproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.swapstyleproject.adapters.ClothingImageAdapter
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityItemInProcessBinding
import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.SwapOfferStatus
import com.example.swapstyleproject.model.User
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class ItemInProcessActivity : AppCompatActivity() {
    private lateinit var binding: ActivityItemInProcessBinding

    private val repository = FirebaseRepository.getInstance()
    private val itemRepository = repository.itemRepository
    private val userRepository = repository.userRepository
    private val swapRepository = repository.swapRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItemInProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val itemId = intent.getStringExtra("itemId") ?: run {
            Toast.makeText(this, "Error: Item ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupBackButton()
        loadItemDetails(itemId)
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadItemDetails(itemId: String) {
        lifecycleScope.launch {
            try {
                // 1. First load item details
                itemRepository.getItemById(itemId)
                    .onSuccess { item ->
                        // Setup image slider
                        val imageAdapter = ClothingImageAdapter(item.photos)
                        binding.itemImageViewPager.adapter = imageAdapter

                        // Setup indicator dots
                        TabLayoutMediator(binding.imageIndicator, binding.itemImageViewPager) { _, _ -> }
                            .attach()
                        binding.imageIndicator.visibility =
                            if (item.photos.size > 1) View.VISIBLE else View.GONE

                        // Setup item details
                        binding.itemTitleTextView.text = item.title
                        binding.itemDetailsTextView.text = "${item.brand} | ${item.size} | ${item.category}"
                        binding.descriptionTextView.text = item.description
                    }
                    .onFailure { exception ->
                        Toast.makeText(this@ItemInProcessActivity,
                            "Error loading item: ${exception.message}",
                            Toast.LENGTH_SHORT).show()
                        finish()
                    }

                // 2. Load the accepted swap offer for this item
                loadAcceptedSwapOffer(itemId)
            } catch (e: Exception) {
                Toast.makeText(this@ItemInProcessActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadAcceptedSwapOffer(itemId: String) {
        try {
            // Case 1: Check if this item is the target of an accepted swap
            var foundOffer = false

            swapRepository.getItemSwapOffers(itemId, SwapOfferStatus.ACCEPTED)
                .collect { offers ->
                    if (offers.isNotEmpty()) {
                        val acceptedOffer = offers.first()
                        displaySwapDetails(acceptedOffer, isOfferedItem = false)
                        foundOffer = true
                        return@collect
                    }
                }

            // Case 2: If not found, check if this item is being offered in an accepted swap
            if (!foundOffer) {
                //find swaps where offeredItemId matches
                swapRepository.findSwapsByOfferedItem(itemId, SwapOfferStatus.ACCEPTED)
                    .collect { offers ->
                        if (offers.isNotEmpty()) {
                            val acceptedOffer = offers.first()
                            displaySwapDetails(acceptedOffer, isOfferedItem = true)
                            foundOffer = true
                            return@collect
                        }
                    }
            }

            // If still no offer found
            if (!foundOffer) {
                Toast.makeText(
                    this@ItemInProcessActivity,
                    "No accepted swap found for this item",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this@ItemInProcessActivity,
                "Error loading swap details: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // Method to handle displaying swap details with the correct perspective
    private fun displaySwapDetails(offer: SwapOffer, isOfferedItem: Boolean) {
        // If isOfferedItem is true, then the current item is the offered item in the swap
        // We need to swap the display logic for certain fields

        // Display swap location details
        binding.locationNameTextView.text = offer.selectedLocation.name
        binding.locationAddressTextView.text = offer.selectedLocation.address

        // Display meeting time
        binding.meetingTimeTextView.text = offer.selectedTimeSlot

        if (isOfferedItem) {
            // This item is being offered, so we need to show the target item as the "other" item
            binding.swapDetailsHeader.text = "You offered this item for a swap"
            displayTargetItemDetails(offer)
            // Load the user who owns the target item
            loadUserDetails(offer.itemOwnerId)
        } else {
            // Standard case - this item is the target, show the offered item
            binding.swapDetailsHeader.text = "Someone offered to swap for this item"
            displayOfferedItemDetails(offer)
            // Load the user who is interested in this item
            loadUserDetails(offer.interestedUserId)
        }
    }

    // New method to display target item details
    private fun displayTargetItemDetails(offer: SwapOffer) {
        binding.apply {
            // Make sure the offered item section is visible
            offeredItemTitle.visibility = View.VISIBLE
            offeredItemDetails.visibility = View.VISIBLE
            offeredItemImage.visibility = View.VISIBLE

            offeredItemTitle.text = offer.itemTitle
            offeredItemDetails.text = "Click to view details"

            // Load the first image of the target item
            if (offer.itemPhotoUrls.isNotEmpty()) {
                Glide.with(this@ItemInProcessActivity)
                    .load(offer.itemPhotoUrls[0])
                    .placeholder(R.drawable.ic_add_photo)
                    .error(R.drawable.ic_add_photo)
                    .centerCrop()
                    .into(offeredItemImage)
            }

            // Make the section clickable to see details
            val offeredItemContainer = offeredItemImage.parent as View
            offeredItemContainer.setOnClickListener {
                navigateToOfferedItemDetails(offer.itemId)
            }
        }
    }

    private fun displayOfferedItemDetails(offer: SwapOffer) {
        if (offer.offeredItemId.isEmpty()) {
            // If there's no offered item (older swap offers might not have this), hide the section
            binding.offeredItemTitle.visibility = View.GONE
            binding.offeredItemDetails.visibility = View.GONE
            binding.offeredItemImage.visibility = View.GONE
            return
        }

        binding.offeredItemTitle.text = offer.offeredItemTitle
        binding.offeredItemDetails.text = "Click to view details"

        // Load the first image of the offered item
        if (offer.offeredItemPhotoUrls.isNotEmpty()) {
            binding.offeredItemImage.visibility = View.VISIBLE
            Glide.with(this@ItemInProcessActivity)
                .load(offer.offeredItemPhotoUrls[0])
                .placeholder(R.drawable.ic_add_photo)
                .error(R.drawable.ic_add_photo)
                .centerCrop()
                .into(binding.offeredItemImage)
        } else {
            binding.offeredItemImage.visibility = View.GONE
        }

        // Make the offered item section clickable to see details
        binding.offeredItemImage.setOnClickListener {
            navigateToOfferedItemDetails(offer.offeredItemId)
        }
        binding.offeredItemDetails.setOnClickListener {
            navigateToOfferedItemDetails(offer.offeredItemId)
        }
        binding.offeredItemTitle.setOnClickListener {
            navigateToOfferedItemDetails(offer.offeredItemId)
        }
    }

    private fun navigateToOfferedItemDetails(itemId: String) {
        if (itemId.isEmpty()) return

        // Navigate to ItemDetailsConsumerActivity
        val intent = Intent(this, ItemDetailsConsumerActivity::class.java)
        intent.putExtra("itemId", itemId)
        intent.putExtra("fromInProcessScreen", true)
        startActivity(intent)
    }

    private fun loadUserDetails(userId: String) {
        lifecycleScope.launch {
            userRepository.getUserProfile(userId)
                .onSuccess { user ->
                    updateUserUI(user)
                }
                .onFailure { exception ->
                    Toast.makeText(this@ItemInProcessActivity,
                        "Error loading user: ${exception.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserUI(user: User) {
        binding.userNameTextView.text = user.username
        binding.swapCountTextView.text = "${user.swapCount} Swaps Completed"

        // Load profile image
        Glide.with(this)
            .load(user.profileImageUrl)
            .placeholder(R.drawable.ic_profile)
            .error(R.drawable.ic_profile)
            .into(binding.userProfileImage)
    }

}