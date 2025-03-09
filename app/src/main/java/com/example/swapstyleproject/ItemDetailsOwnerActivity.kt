package com.example.swapstyleproject

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.swapstyleproject.adapters.ClothingImageAdapter
import com.example.swapstyleproject.adapters.SwapOffersAdapter
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityItemDetailsOwnerBinding
import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.SwapOfferStatus
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.OnBackPressedCallback
import com.bumptech.glide.Glide
import com.example.swapstyleproject.model.ItemStatus
import com.example.swapstyleproject.utilities.AnimationHelper

class ItemDetailsOwnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityItemDetailsOwnerBinding
    private lateinit var swapOffersAdapter: SwapOffersAdapter

    private var selectedOffer: SwapOffer? = null
    private var isProcessingAcceptance = false

    private val repository = FirebaseRepository.getInstance()
    private val itemRepository = repository.itemRepository
    private val swapRepository = repository.swapRepository
    private val userRepository = repository.userRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItemDetailsOwnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isProcessingAcceptance) {
                    finish()
                }
            }
        })

        val itemId = intent.getStringExtra("itemId") ?: run {
            Toast.makeText(this, "Error: Item ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupSwapOffersList()
        setupConfirmButton()

        loadItemDetails(itemId)
        loadSwapOffers(itemId)
    }

    private fun initializeViews() {
        binding.confirmSwapButton.apply {
            isEnabled = false
            text = "Select a swap offer"
        }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            if (!isProcessingAcceptance) {
                finish()
            }
        }
    }

    private fun setupSwapOffersList() {
        swapOffersAdapter = SwapOffersAdapter(
            onOfferSelected = { offer ->
                handleOfferSelection(offer)
            },onOfferedItemClick = { offeredItemId ->
                showOfferedItemDetailsDialog(offeredItemId)
            }
        )

        binding.swapOffersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ItemDetailsOwnerActivity)
            adapter = swapOffersAdapter
        }
    }

    private fun showOfferedItemDetailsDialog(offeredItemId: String) {
        lifecycleScope.launch {
            try {
                val itemResult = itemRepository.getItemById(offeredItemId)

                withContext(Dispatchers.Main) {

                    itemResult.onSuccess { item ->
                        val dialogView = layoutInflater.inflate(R.layout.dialog_offered_item_details, null)

                        val itemImage: ImageView = dialogView.findViewById(R.id.itemImage)
                        val itemTitle: TextView = dialogView.findViewById(R.id.itemTitle)
                        val itemDetails: TextView = dialogView.findViewById(R.id.itemDetails)
                        val descriptionText: TextView = dialogView.findViewById(R.id.descriptionText)

                        itemTitle.text = item.title
                        itemDetails.text = "${item.brand} | ${item.size} | ${item.category}"
                        descriptionText.text = item.description

                        if (item.photos.isNotEmpty()) {
                            Glide.with(this@ItemDetailsOwnerActivity)
                                .load(item.photos[0])
                                .placeholder(R.drawable.ic_add_photo)
                                .error(R.drawable.ic_add_photo)
                                .into(itemImage)
                        }

                        val dialog = AlertDialog.Builder(this@ItemDetailsOwnerActivity, R.style.CustomAlertDialog)
                            .setView(dialogView)
                            .setPositiveButton("Close") { dialogInterface, _ ->
                                dialogInterface.dismiss()
                            }
                            .create()

                        dialog.show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ItemDetailsOwnerActivity,
                        "Failed to load item details: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupConfirmButton() {
        binding.confirmSwapButton.setOnClickListener {
            selectedOffer?.let { offer ->
                showConfirmationDialog(offer)
            }
        }
    }

    private fun loadItemDetails(itemId: String) {
        lifecycleScope.launch {
            try {
                itemRepository.getItemById(itemId)
                    .onSuccess { item ->
                        val imageAdapter = ClothingImageAdapter(item.photos)
                        binding.itemImageViewPager.adapter = imageAdapter

                        TabLayoutMediator(binding.imageIndicator, binding.itemImageViewPager) { _, _ -> }
                            .attach()

                        binding.itemTitleTextView.text = item.title
                        binding.itemDetailsTextView.text = "${item.brand} | ${item.size} | ${item.category}"

                        binding.imageIndicator.visibility =
                            if (item.photos.size > 1) View.VISIBLE else View.GONE
                    }
                    .onFailure { throwable ->
                        showError("Error loading item details", throwable)
                        finish()
                    }
            } catch (throwable: Throwable) {
                showError("Unexpected error", throwable)
                finish()
            }
        }
    }

    private fun loadSwapOffers(itemId: String) {
        lifecycleScope.launch {
            try {
                // Load only pending offers since accepted offers are now shown in a different screen
                swapRepository.getItemSwapOffers(itemId, SwapOfferStatus.PENDING).collectLatest { pendingOffers ->
                    if (pendingOffers.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        swapOffersAdapter.submitList(pendingOffers)
                    }
                }
            } catch (throwable: Throwable) {
                showError("Error loading swap suggestions", throwable)
            }
        }
    }

    private fun handleOfferSelection(offer: SwapOffer) {
        // בודק אם הפריט שהוצע עדיין זמין
        if (!offer.offeredItemId.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    itemRepository.isItemAvailableForSwap(offer.offeredItemId)
                        .onSuccess { isAvailable ->
                            if (isAvailable) {
                                // פריט זמין - מאפשר בחירה
                                selectedOffer = offer
                                binding.confirmSwapButton.isEnabled = true
                                binding.confirmSwapButton.text = "Swap confirmation"
                            } else {
                                // פריט לא זמין - מונע בחירה ומציג הודעה
                                Toast.makeText(
                                    this@ItemDetailsOwnerActivity,
                                    "This item is no longer available for swap",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // מסיר בחירה אם קיימת
                                if (selectedOffer?.offeredItemId == offer.offeredItemId) {
                                    selectedOffer = null
                                    binding.confirmSwapButton.isEnabled = false
                                    binding.confirmSwapButton.text = "Select a swap offer"
                                }
                            }
                        }
                        .onFailure { exception ->
                            // טיפול בשגיאה
                        }
                } catch (e: Exception) {
                }
            }
        } else {
            // מקרה שאין מזהה לפריט המוצע
            selectedOffer = offer
            binding.confirmSwapButton.isEnabled = true
            binding.confirmSwapButton.text = "Swap confirmation"
        }
    }

    private fun showConfirmationDialog(offer: SwapOffer) {
        // בדיקה נוספת לפני הצגת דיאלוג האישור
        if (!offer.offeredItemId.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    itemRepository.getItemById(offer.offeredItemId)
                        .onSuccess { item ->
                            if (item.status != ItemStatus.AVAILABLE) {
                                Toast.makeText(
                                    this@ItemDetailsOwnerActivity,
                                    "This item is no longer available for swap",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // מעדכן את הרשימה
                                loadSwapOffers(offer.itemId)
                                return@launch
                            }

                            // ממשיך להצגת דיאלוג האישור
                            showActualConfirmationDialog(offer)
                        }
                        .onFailure {
                            // במקרה של שגיאה בבדיקה, מניח שהפריט זמין
                            showActualConfirmationDialog(offer)
                        }
                } catch (e: Exception) {
                    // במקרה של שגיאה כללית, מניח שהפריט זמין
                    showActualConfirmationDialog(offer)
                }
            }
        } else {
            // אם אין פריט מוצע, ממשיך כרגיל
            showActualConfirmationDialog(offer)
        }
    }

    // פונקציה שמראה את הדיאלוג בפועל (קוד שהיה לפני)
    private fun showActualConfirmationDialog(offer: SwapOffer) {
        lifecycleScope.launch {
            try {
                userRepository.getUserProfile(offer.interestedUserId)
                    .onSuccess { user ->
                        val dialog = AlertDialog.Builder(this@ItemDetailsOwnerActivity)
                            .setTitle("Swap confirmation")
                            .setMessage("""
                        Do you want to approve the swap offer from ${user.username}?
                        
                        Location: ${offer.selectedLocation.name}
                        Time: ${offer.selectedTimeSlot}
                    """.trimIndent())
                            .setPositiveButton("OK") { _, _ ->
                                processSwapAcceptance(offer)
                            }
                            .setNegativeButton("Cancel", null)
                            .create()

                        // Set text alignment for the title
                        dialog.setOnShowListener {
                            val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
                            titleView?.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                            titleView?.gravity = Gravity.START
                        }

                        dialog.show()
                    }
                    .onFailure { throwable ->
                        showError("Error loading user information", throwable)
                    }
            } catch (throwable: Throwable) {
                showError("Unexpected error", throwable)
            }
        }
    }

    private fun processSwapAcceptance(offer: SwapOffer) {
        if (isProcessingAcceptance) return

        isProcessingAcceptance = true
        binding.confirmSwapButton.apply {
            isEnabled = false
            text = "Confirm the Swap..."
        }

        lifecycleScope.launch {
            try {
                // Update swap offer status to ACCEPTED
                swapRepository.updateSwapOfferStatus(offer.swapId, SwapOfferStatus.ACCEPTED)
                    .onSuccess {
                        // Update both items status to IN_PROCESS - this item and the offered item
                        val updateMainItemTask = itemRepository.updateItemStatus(offer.itemId, "IN_PROCESS")
                        val updateOfferedItemTask = if (!offer.offeredItemId.isNullOrEmpty()) {
                            itemRepository.updateItemStatus(offer.offeredItemId, "IN_PROCESS")
                        } else {
                            Result.success(Unit)
                        }

                        // Making sure the suggested item is also updated
                        updateMainItemTask.onSuccess {
                            updateOfferedItemTask.onSuccess {
                                AnimationHelper.showSuccessAnimation(
                                    this@ItemDetailsOwnerActivity,
                                    "The Swap was successfully approved!",
                                    onAnimationEnd = {
                                        val intent = Intent(this@ItemDetailsOwnerActivity, MainActivity::class.java)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        startActivity(intent)
                                        finish()
                                    }
                                )
                            }.onFailure { throwable ->
                                showError("Error updating offered item status", throwable)
                                resetConfirmButton()
                            }
                        }.onFailure { throwable ->
                            showError("Error updating item status", throwable)
                            resetConfirmButton()
                        }
                    }
                    .onFailure { throwable ->
                        showError("Error in approving the proposal", throwable)
                        resetConfirmButton()
                    }
            } catch (throwable: Throwable) {
                showError("Unexpected error", throwable)
                resetConfirmButton()
            }
        }
    }

    private fun resetConfirmButton() {
        isProcessingAcceptance = false
        binding.confirmSwapButton.apply {
            isEnabled = true
            text = "Swap confirmation"
        }
    }

    private fun showEmptyState() {
        binding.swapOffersRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.confirmSwapButton.visibility = View.GONE
        binding.acceptedOfferLayout.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.swapOffersRecyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.confirmSwapButton.visibility = View.VISIBLE
        binding.acceptedOfferLayout.visibility = View.GONE
    }

    private fun showError(message: String, throwable: Throwable) {
        val errorMessage = if (throwable.message.isNullOrEmpty()) {
            message
        } else {
            "$message: ${throwable.message}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }
}