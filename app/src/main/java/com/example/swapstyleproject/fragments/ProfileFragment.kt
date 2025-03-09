package com.example.swapstyleproject.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.swapstyleproject.adapters.ClothingItemsAdapter
import com.example.swapstyleproject.ItemDetailsOwnerActivity
import com.example.swapstyleproject.ItemInProcessActivity
import com.example.swapstyleproject.R
import com.example.swapstyleproject.adapters.SentOffersAdapter
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.data.repository.swap.SwapRepositoryImpl
import com.example.swapstyleproject.databinding.FragmentProfileBinding
import com.example.swapstyleproject.model.ClothingItem
import com.example.swapstyleproject.model.ItemStatus
import com.example.swapstyleproject.model.User
import com.example.swapstyleproject.utilities.ImagePickerHelper
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import com.example.swapstyleproject.utilities.AnimationHelper

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val repository = FirebaseRepository.getInstance()
    private val userRepository = repository.userRepository
    private val itemRepository = repository.itemRepository
    private val swapRepository = SwapRepositoryImpl()

    private lateinit var itemsAdapter: ClothingItemsAdapter
    private lateinit var sentOffersAdapter: SentOffersAdapter
    private lateinit var imagePickerHelper: ImagePickerHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        loadUserProfile()
        loadActiveItems()
    }

    private fun setupViews() {
        setupRecyclerView()
        setupTabLayout()
        setupImagePicker()
    }

    private fun setupRecyclerView() {
        itemsAdapter = ClothingItemsAdapter(
            onFavoriteClick = { itemId, isFavorite -> handleFavoriteClick(itemId, isFavorite) },
            onItemClick = { item ->
                // Check item status and navigate to appropriate screen
                if (item.isOwnItem) {
                    when (item.status) {
                        ItemStatus.AVAILABLE -> {
                            // Navigate to ItemDetailsOwnerActivity for available items
                            val intent = Intent(requireContext(), ItemDetailsOwnerActivity::class.java).apply {
                                putExtra("itemId", item.id)
                            }
                            startActivity(intent)
                        }
                        ItemStatus.IN_PROCESS -> {
                            // Navigate to ItemInProcessActivity for in-process items
                            val intent = Intent(requireContext(), ItemInProcessActivity::class.java).apply {
                                putExtra("itemId", item.id)
                            }
                            startActivity(intent)
                        }
                        else -> {
                            // For other statuses like SWAPPED or UNAVAILABLE, you might not need any action
                            // or you could create separate screens for those too
                        }
                    }
                }
            }, onItemDelete = { item ->
                showItemDeletionConfirmationDialog(item)
            },
            showStatus = true
        )

        sentOffersAdapter = SentOffersAdapter()

        binding.itemsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.itemsRecyclerView.adapter = itemsAdapter
    }

    private fun setupTabLayout() {
        binding.profileTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Active Items
                        binding.itemsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
                        binding.itemsRecyclerView.adapter = itemsAdapter
                        loadActiveItems()
                    }
                    1 -> { // In Process Items
                        binding.itemsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
                        binding.itemsRecyclerView.adapter = itemsAdapter
                        loadInProcessItems()
                    }
                    2 -> { // History
                        binding.itemsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
                        binding.itemsRecyclerView.adapter = itemsAdapter
                        loadHistoryItems()
                    }
                    3 -> { // Favorites
                        binding.itemsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
                        binding.itemsRecyclerView.adapter = itemsAdapter
                        loadFavoriteItems()
                    }
                    4 -> { // Sent Offers
                        binding.itemsRecyclerView.layoutManager =
                            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
                        binding.itemsRecyclerView.adapter = sentOffersAdapter
                        loadSentOffers()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }


    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(
            fragment = this,
            onImageUploaded = { imageUrl, _ ->
                updateUserProfileImage(imageUrl)
            },
            uploadType = ImagePickerHelper.ImageUploadType.PROFILE
        )

        binding.profileImage.setOnClickListener {
            imagePickerHelper.requestPermissionsAndShowDialog(requireContext())
        }
    }

    private fun updateUserProfileImage(imageUrl: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            userRepository.updateUserProfileImage(imageUrl)
                .onSuccess {
                    Glide.with(requireContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(binding.profileImage)
                }
                .onFailure { exception ->
                    Toast.makeText(
                        context,
                        "Failed to update profile image: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun loadUserProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            userRepository.getCurrentUserProfile()
                .onSuccess { user ->
                    updateUIWithUserData(user)
                }
                .onFailure { exception ->
                    Toast.makeText(
                        context,
                        "Failed to load profile: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun updateUIWithUserData(user: User) {
        binding.apply {
            usernameTextView.text = user.username
            userLevelTextView.text = user.userLevel
            swapsCountTextView.text = "${user.swapCount} Swaps Completed"

            // Load profile image
            Glide.with(requireContext())
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(profileImage)
        }
    }

    private fun loadActiveItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            itemRepository.getUserItemsByStatus(ItemStatus.AVAILABLE.name)
                .onSuccess { items ->
                    updateItemsList(items)
                }
                .onFailure { exception ->
                    showEmptyState()
                }
        }
    }

    private fun loadInProcessItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            // First check for expired items
            itemRepository.checkAndUpdateExpiredItems()
                .onSuccess {
                }
                .onFailure { exception ->
                }

            // Then load the in-process items
            itemRepository.getUserItemsByStatus(ItemStatus.IN_PROCESS.name)
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        itemsAdapter.submitList(items)
                    }
                }
                .onFailure { exception ->
                    showEmptyState()
                }
        }
    }

    private fun loadHistoryItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get items with both SWAPPED and UNAVAILABLE statuses
            val swappedItemsResult = itemRepository.getUserItemsByStatus(ItemStatus.SWAPPED.name)
            val unavailableItemsResult = itemRepository.getUserItemsByStatus(ItemStatus.UNAVAILABLE.name)

            val combinedItems = mutableListOf<ClothingItem>()

            swappedItemsResult.onSuccess { items ->
                combinedItems.addAll(items)
            }

            unavailableItemsResult.onSuccess { items ->
                combinedItems.addAll(items)
            }

            if (combinedItems.isNotEmpty()) {
                updateItemsList(combinedItems)
            } else {
                showEmptyState()
            }
        }
    }

    private fun loadFavoriteItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            itemRepository.getFavoriteItemIds()
                .onSuccess { favoriteIds ->
                    val items = favoriteIds.mapNotNull { id ->
                        itemRepository.getItemById(id).getOrNull()?.copy(isFavorite = true)
                    }
                    updateItemsList(items)
                }
                .onFailure { exception ->
                    Toast.makeText(
                        context,
                        "Failed to load favorites: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun updateItemsList(items: List<ClothingItem>) {
        if (items.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
            itemsAdapter.submitList(items)
        }
    }

    private fun loadSentOffers() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Check and update expired offers first
            itemRepository.checkAndUpdateExpiredItems()
                .onFailure { exception ->
                }

            swapRepository.getUserSentOffers()
                .collect { offers ->
                    if (offers.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        sentOffersAdapter.submitList(offers)
                    }
                }
        }
    }

    private fun handleFavoriteClick(itemId: String, isFavorite: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            // In the profile, only remove from favorites
            val result = itemRepository.removeFromFavorites(itemId)

            result.onSuccess {
                // Update the local list by removing the item
                val currentList = itemsAdapter.currentList.toMutableList()
                currentList.removeAll { it.id == itemId }
                itemsAdapter.submitList(currentList)
            }.onFailure { exception ->
                Toast.makeText(
                    requireContext(),
                    "Failed to remove from favorites: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showItemDeletionConfirmationDialog(item: ClothingItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete this item? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem(item: ClothingItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                itemRepository.deleteItem(item.id)
                    .onSuccess {
                        // Remove item from the list
                        val currentList = itemsAdapter.currentList.toMutableList()
                        currentList.remove(item)
                        itemsAdapter.submitList(currentList)

                        // Show success animation
                        AnimationHelper.showSuccessAnimation(
                            requireContext(),
                            "Item deleted successfully"
                        )
                    }
                    .onFailure { exception ->
                        Toast.makeText(
                            requireContext(),
                            "Failed to delete item: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error deleting item: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.itemsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.itemsRecyclerView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}