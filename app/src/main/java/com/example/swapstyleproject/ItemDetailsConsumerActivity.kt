package com.example.swapstyleproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.swapstyleproject.adapters.ClothingImageAdapter
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityItemDetailsConsumerBinding
import com.example.swapstyleproject.model.ClothingItem
import com.example.swapstyleproject.utilities.FavoriteButtonHelper
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import android.view.View
import com.example.swapstyleproject.model.User

class ItemDetailsConsumerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityItemDetailsConsumerBinding

    private val repository = FirebaseRepository.getInstance()
    private val itemRepository = repository.itemRepository
    private val userRepository = repository.userRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItemDetailsConsumerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val itemId = intent.getStringExtra("itemId") ?: return
        val fromInProcessScreen = intent.getBooleanExtra("fromInProcessScreen", false)

        setupBackButton()

        // Hide the offer swap button if coming from the in-process screen
        if (fromInProcessScreen) {
            binding.offerSwapButton.visibility = View.GONE
        } else {
            setupSwapButton(itemId)
        }
        loadItemDetails(itemId)
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupSwapButton(itemId: String) {
        binding.offerSwapButton.setOnClickListener {
            val intent = Intent(this, OfferSwapActivity::class.java)
            intent.putExtra("itemId", itemId)
            startActivity(intent)
        }
    }

    private fun loadItemDetails(itemId: String) {
        lifecycleScope.launch {
            itemRepository.getItemById(itemId)
                .onSuccess { item ->
                    setupItemDetails(item)
                    loadSellerDetails(item.userId)
                    setupFavoriteButton(item)
                }
                .onFailure { exception ->
                    handleError("Failed to load item details: ${exception.message}")
                }
        }
    }

    private fun setupItemDetails(item: ClothingItem) {
        // Set up images ViewPager with adapter
        val imageAdapter = ClothingImageAdapter(item.photos)
        binding.itemImageViewPager.adapter = imageAdapter

        // Set up image indicator
        TabLayoutMediator(binding.imageIndicator, binding.itemImageViewPager) { _, _ -> }.attach()
        binding.imageIndicator.visibility = if (item.photos.size > 1) View.VISIBLE else View.GONE

        // Set item details
        binding.itemTitleTextView.text = item.title
        binding.itemDetailsTextView.text = "${item.brand} | ${item.size} | ${item.category}"
        binding.descriptionTextView.text = item.description
    }

    private fun loadSellerDetails(sellerId: String) {
        lifecycleScope.launch {
            userRepository.getUserProfile(sellerId)
                .onSuccess { user ->
                    updateSellerUI(user)
                }
                .onFailure { exception ->
                    handleError("Failed to load seller details: ${exception.message}")
                }
        }
    }

    private fun updateSellerUI(user: User) {
        binding.apply {
            usernameTextView.text = user.username
            swapsCountTextView.text = "${user.swapCount} Swaps Completed"

            // Load profile image
            Glide.with(this@ItemDetailsConsumerActivity)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(userProfileImage)
        }
    }

    private fun setupFavoriteButton(item: ClothingItem) {
        var currentFavoriteState = item.isFavorite

        // Initial button state setup
        FavoriteButtonHelper.updateFavoriteButton(currentFavoriteState, binding.favoriteButton)

        binding.favoriteButton.setOnClickListener {
            val newFavoriteState = !currentFavoriteState

            // Update UI immediately
            FavoriteButtonHelper.updateFavoriteButton(newFavoriteState, binding.favoriteButton)

            lifecycleScope.launch {
                val result = if (newFavoriteState) {
                    itemRepository.addToFavorites(item.id)
                } else {
                    itemRepository.removeFromFavorites(item.id)
                }

                result.onSuccess {
                    currentFavoriteState = newFavoriteState
                    // Send result back to HomeFragment
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("itemId", item.id)
                        putExtra("isFavorite", currentFavoriteState)
                    })
                }.onFailure { exception ->
                    // Revert UI on failure
                    FavoriteButtonHelper.updateFavoriteButton(currentFavoriteState, binding.favoriteButton)
                    handleError("Failed to update favorite status: ${exception.message}")
                }
            }
        }
    }

    private fun handleError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh item data if needed
        val itemId = intent.getStringExtra("itemId") ?: return
        loadItemDetails(itemId)
    }

}