package com.example.swapstyleproject.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.swapstyleproject.adapters.ClothingItemsAdapter
import com.example.swapstyleproject.MainActivity
import com.example.swapstyleproject.R
import com.example.swapstyleproject.databinding.FragmentHomeBinding
import com.example.swapstyleproject.utilities.ImagePickerHelper
import com.google.android.material.tabs.TabLayout
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.swapstyleproject.ItemDetailsConsumerActivity
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.model.User

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val repository = FirebaseRepository.getInstance()
    private val userRepository = repository.userRepository
    private val itemRepository = repository.itemRepository

    private lateinit var itemsAdapter: ClothingItemsAdapter
    private lateinit var imagePickerHelper: ImagePickerHelper

    private lateinit var itemDetailsLauncher: ActivityResultLauncher<Intent>

    private val categories = listOf("See All", "Women", "Men", "Kids", "Accessories")
    private var searchJob: Job? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBarLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray))

        // Initializing the Launcher to receive results from the ItemDetailsConsumerActivity
        //this is part of a mechanism that allows the HomeFragment to know whether the user marked/unmarked an item as
        // a "favorite" while in the item details screen
        itemDetailsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val data = result.data
                val itemId = data?.getStringExtra("itemId") ?: return@registerForActivityResult
                val isFavorite = data.getBooleanExtra("isFavorite", false)
                updateItemFavoriteState(itemId, isFavorite)
            }
        }

        setupBackgroundColor()
        setupViews()
        loadUserInfo()
        loadItems("See All")
    }

    //This function allows for an immediate visual update of the favorites state
    // for an item, without reloading the entire list from the server.
    private fun updateItemFavoriteState(itemId: String, isFavorite: Boolean) {
        val currentList = itemsAdapter.currentList.toMutableList()
        val itemIndex = currentList.indexOfFirst { it.id == itemId }
        if (itemIndex != -1) {
            val updatedItem = currentList[itemIndex].copy(isFavorite = isFavorite)
            currentList[itemIndex] = updatedItem
            itemsAdapter.submitList(currentList)
        }
    }

    private fun setupBackgroundColor() {
        binding.appBarLayout.setBackgroundColor(
            requireContext().getColor(R.color.gray)
        )
    }

    private fun setupViews() {
        setupToolbar()
        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupImagePicker()
        setupEmptyState()
    }

    private fun setupToolbar() {
        // Menu button
        binding.menuButton.setOnClickListener {
            (requireActivity() as MainActivity).openDrawer()
        }

        // Profile image click
        binding.profileImage.setOnClickListener {
            imagePickerHelper.uploadType = ImagePickerHelper.ImageUploadType.PROFILE
            imagePickerHelper.requestPermissionsAndShowDialog(requireContext())
        }
    }

    //Sets the items view
    private fun setupRecyclerView() {
        itemsAdapter = ClothingItemsAdapter(
            onFavoriteClick = { itemId, isFavorite ->
                handleFavoriteClick(itemId, isFavorite)
            },
            onItemClick = { item ->
                val intent = Intent(requireContext(), ItemDetailsConsumerActivity::class.java)
                intent.putExtra("itemId", item.id)
                itemDetailsLauncher.launch(intent)
            }
        )

        binding.itemsRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = itemsAdapter
        }
    }

    private fun setupTabs() {
        // Remove any existing tabs to prevent duplicates
        binding.categoryTabs.apply {
            removeAllTabs() // Clear existing tabs
            categories.forEach { category ->
                addTab(newTab().setText(category))
            }

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.text?.toString()?.let { category ->
                        loadItems(category)
                    }
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    //Defines the search system
    private fun setupSearch() {
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300) // Debounce delay
                    performSearch(s?.toString() ?: "")
                }
            }
        })

        binding.searchEdit.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                hideKeyboard(v)
                true
            } else {
                false
            }
        }
    }

    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(
            fragment = this,
            onImageUploaded = { imageUrl, _ ->
                updateUserProfileImage(imageUrl)
            },
            uploadType = ImagePickerHelper.ImageUploadType.PROFILE
        )
    }

    private fun setupEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
    }

    //Performing the actual search
    private fun performSearch(query: String) {
        if (query.isBlank()) {
            loadItems("See All")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            itemRepository.searchItems(query)
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        itemsAdapter.submitList(items)
                    }
                }
                .onFailure { exception ->
                    Toast.makeText(context, exception.message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            userRepository.getCurrentUserProfile()
                .onSuccess { user ->
                    updateUserUI(user)
                }
                .onFailure { exception ->
                    Toast.makeText(context, exception.message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserUI(user: User) {
        binding.welcomeText.text = "Welcome, ${user.username}"

        Glide.with(requireContext())
            .load(user.profileImageUrl)
            .placeholder(R.drawable.ic_profile)
            .error(R.drawable.ic_profile)
            .into(binding.profileImage)
    }


    private fun loadItems(category: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val updateResult = itemRepository.checkAndUpdateExpiredItems()
            updateResult.onFailure { exception ->
            }
            itemRepository.getItems(category = category)
                .collect { items ->
                    if (items.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        itemsAdapter.submitList(items)
                    }
                }
        }
    }

    private fun handleFavoriteClick(itemId: String, newFavoriteState: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (newFavoriteState) {
                itemRepository.addToFavorites(itemId)
            } else {
                itemRepository.removeFromFavorites(itemId)
            }

            result.onSuccess {
                updateItemFavoriteState(itemId, newFavoriteState)
            }.onFailure { exception ->
                Toast.makeText(
                    requireContext(),
                    "Failed to update favorites: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.itemsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.itemsRecyclerView.visibility = View.VISIBLE
    }


    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}