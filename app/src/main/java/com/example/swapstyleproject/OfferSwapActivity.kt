package com.example.swapstyleproject

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.swapstyleproject.adapters.ItemSelectionAdapter
import com.example.swapstyleproject.adapters.PlaceSuggestionAdapter
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityOfferSwapBinding
import com.example.swapstyleproject.databinding.DialogSelectItemBinding
import com.example.swapstyleproject.model.ClothingItem
import com.example.swapstyleproject.model.ItemStatus
import com.example.swapstyleproject.model.PlaceSuggestion
import com.example.swapstyleproject.model.SwapLocation
import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.SwapOfferStatus
import com.example.swapstyleproject.utilities.LocationService
import com.example.swapstyleproject.utilities.LocationUtils
import com.example.swapstyleproject.utilities.MapManager
import com.example.swapstyleproject.utilities.AnimationHelper
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class OfferSwapActivity : AppCompatActivity(),OnMapReadyCallback  {
    private lateinit var binding: ActivityOfferSwapBinding
    private lateinit var mapManager: MapManager
    private lateinit var locationService: LocationService
    private lateinit var placeSuggestionAdapter: PlaceSuggestionAdapter
    private lateinit var itemSelectionAdapter: ItemSelectionAdapter

    private val repository = FirebaseRepository.getInstance()
    private val itemRepository = repository.itemRepository
    private val swapRepository = repository.swapRepository

    private var searchJob: Job? = null
    private var selectedPlace: PlaceSuggestion? = null
    private var selectedItem: ClothingItem? = null
    private var userItems: List<ClothingItem> = emptyList()
    private var itemSelectionDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfferSwapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the item ID passed from ItemDetailsConsumerActivity
        val itemId = intent.getStringExtra("itemId") ?: run {
            Toast.makeText(this, "Error: Item ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Request location permission
        LocationUtils.requestLocationPermission(
            this,
            onPermissionGranted = {
                setupDependencies()
                setupViews()
                setupMap()
                loadTimeSlots(itemId)
                loadUserAvailableItems()
            },
            onPermissionDenied = {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    private fun setupDependencies() {
        mapManager = MapManager(this)
        locationService = LocationService(getString(R.string.google_maps_key))
    }

    private fun setupViews() {
        setupToolbar()
        setupItemSelection()
        setupLocationSearch()
        setupPlaceSuggestions()
        setupConfirmButton()

        binding.timeSlotRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateConfirmButtonState()
        }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupItemSelection() {
        binding.selectItemCard.setOnClickListener {
            showItemSelectionDialog()
        }

        binding.selectItemButton.setOnClickListener {
            showItemSelectionDialog()
        }
    }

    private fun loadUserAvailableItems() {
        lifecycleScope.launch {
            try {
                val result = itemRepository.getUserItemsByStatus(ItemStatus.AVAILABLE.name)

                result.onSuccess { items ->
                    lifecycleScope.launch {
                        try {
                            val filteredItems = itemRepository.filterItemsNotOffered(items)
                            userItems = filteredItems
                            Log.d("OfferSwapActivity", "Loaded ${filteredItems.size} available user items")

                            // Enable/disable item selection based on available items
                            if (filteredItems.isEmpty()) {
                                binding.selectItemCard.isEnabled = false
                                binding.selectedItemDetails.text = "You don't have any available items to offer"
                            } else {
                                binding.selectItemCard.isEnabled = true
                            }
                        } catch (e: Exception) {
                            Log.e("OfferSwapActivity", "Error filtering offered items", e)
                        }
                    }
                }.onFailure { exception ->
                    Log.e("OfferSwapActivity", "Error loading user items", exception)
                    Toast.makeText(
                        this@OfferSwapActivity,
                        "Error loading your items: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("OfferSwapActivity", "Exception loading user items", e)
            }
        }
    }

    private fun showItemSelectionDialog() {
        if (userItems.isEmpty()) {
            Toast.makeText(this, "You don't have any available items to offer", Toast.LENGTH_SHORT).show()
            return
        }
        // Create dialog with custom view
        val dialogBinding = DialogSelectItemBinding.inflate(layoutInflater)
        itemSelectionDialog = Dialog(this, R.style.CustomAlertDialog).apply {
            setContentView(dialogBinding.root)
            setCancelable(true)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // Initialize the adapter
        itemSelectionAdapter = ItemSelectionAdapter { item ->
            // Enable select button when an item is selected
            dialogBinding.selectButton.isEnabled = true
        }

        // Set up RecyclerView
        dialogBinding.availableItemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OfferSwapActivity)
            adapter = itemSelectionAdapter
        }

        // Submit the list of available items
        itemSelectionAdapter.submitList(userItems)

        // Set up select button
        dialogBinding.selectButton.setOnClickListener {
            val selectedItem = itemSelectionAdapter.getSelectedItem()
            if (selectedItem != null) {
                this.selectedItem = selectedItem
                updateSelectedItemUI(selectedItem)
                itemSelectionDialog?.dismiss()
                updateConfirmButtonState()
            }
        }

        // Set up cancel button
        dialogBinding.cancelButton.setOnClickListener {
            itemSelectionDialog?.dismiss()
        }

        // Show the dialog
        itemSelectionDialog?.show()
    }

    private fun updateSelectedItemUI(item: ClothingItem) {
        binding.apply {
            selectedItemTitle.text = item.title
            selectedItemDetails.text = "${item.brand} | ${item.size} | ${item.category}"
            noItemSelectedText.visibility = View.GONE

            // Load the first image
            if (item.photos.isNotEmpty()) {
                Glide.with(this@OfferSwapActivity)
                    .load(item.photos[0])
                    .centerCrop()
                    .into(selectedItemImage)
            }
        }
    }

    private fun setupLocationSearch() {
        binding.searchLocationInput.addTextChangedListener { editable ->
            searchJob?.cancel()

            if (editable?.isNotEmpty() == true) {
                binding.placeSuggestionsRecyclerView.visibility = View.VISIBLE

                searchJob = lifecycleScope.launch {
                    delay(300)
                    performPlaceSearch(editable.toString())
                }
            } else {
                binding.placeSuggestionsRecyclerView.visibility = View.GONE
                binding.selectedLocationCard.visibility = View.GONE
            }
        }

        binding.searchLocationLayout.setEndIconOnClickListener {
            binding.searchLocationInput.text?.clear()
            binding.selectedLocationCard.visibility = View.GONE
            binding.selectedLocationName.text = ""
            binding.selectedLocationAddress.text = ""
            binding.selectedLocationDistance.text = ""
            returnToCurrentLocation()
        }
    }

    private fun returnToCurrentLocation() {
        lifecycleScope.launch {
            val currentLocationResult = LocationUtils.getCurrentLocation(this@OfferSwapActivity)
            val currentLocation = currentLocationResult.getOrNull()

            currentLocation?.let { location ->
                mapManager.updateMapLocation(location)
            } ?: run {
                Toast.makeText(
                    this@OfferSwapActivity,
                    "Unable to locate current location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupPlaceSuggestions() {
        placeSuggestionAdapter = PlaceSuggestionAdapter { place ->
            handlePlaceSelection(place)
        }

        binding.placeSuggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OfferSwapActivity)
            adapter = placeSuggestionAdapter
        }
    }

    private fun setupConfirmButton() {
        binding.confirmSwapButton.apply {
            isEnabled = false

            setOnClickListener {
                val selectedTimeSlotButton = binding.timeSlotRadioGroup
                    .findViewById<RadioButton>(binding.timeSlotRadioGroup.checkedRadioButtonId)

                val timeSlot = selectedTimeSlotButton?.text?.toString()
                    ?: return@setOnClickListener

                val itemId = intent.getStringExtra("itemId")
                    ?: return@setOnClickListener

                val place = selectedPlace ?: return@setOnClickListener
                val offeredItem = selectedItem ?: return@setOnClickListener

                val swapLocation = SwapLocation(
                    name = place.name,
                    address = place.address,
                    latitude = place.latLng.latitude,
                    longitude = place.latLng.longitude
                )
                createSwapOffer(
                    itemId = itemId,
                    timeSlot = timeSlot,
                    location = swapLocation,
                    offeredItem = offeredItem

                )
            }
        }
    }

    private fun updateConfirmButtonState() {
        val isLocationSelected = selectedPlace != null
        val isTimeSlotSelected = binding.timeSlotRadioGroup.checkedRadioButtonId != -1
        val isItemSelected = selectedItem != null

        binding.confirmSwapButton.isEnabled = isLocationSelected && isTimeSlotSelected && isItemSelected
    }

    private fun createSwapOffer(itemId: String, timeSlot: String, location: SwapLocation,  offeredItem: ClothingItem) {
        lifecycleScope.launch {
            try {
                binding.confirmSwapButton.isEnabled = false
                binding.confirmSwapButton.text = "Sending offer..."

                val itemResult = itemRepository.getItemById(itemId).getOrThrow()

                val swapOffer = SwapOffer(
                    itemId = itemId,
                    itemTitle = itemResult.title,
                    itemPhotoUrls = itemResult.photos,
                    itemOwnerId = itemResult.userId,
                    interestedUserId = FirebaseAuth.getInstance().currentUser?.uid
                        ?: throw IllegalStateException("No user logged in"),
                    offeredItemId = offeredItem.id,
                    offeredItemTitle = offeredItem.title,
                    offeredItemPhotoUrls = offeredItem.photos,
                    selectedLocation = location,
                    selectedTimeSlot = timeSlot,
                    status = SwapOfferStatus.PENDING
                )

                swapRepository.createSwapOffer(swapOffer)
                    .onSuccess {
                        AnimationHelper.showSuccessAnimation(
                            this@OfferSwapActivity,
                            "The offer was sent successfully!",
                            onAnimationEnd = {
                                val intent = Intent(this@OfferSwapActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                startActivity(intent)
                                finish()
                            }
                        )
                    }
                    .onFailure { exception ->
                        Toast.makeText(
                            this@OfferSwapActivity,
                            "Error sending proposal: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        binding.confirmSwapButton.isEnabled = true
                        binding.confirmSwapButton.text = "Replacement confirmation"
                    }

            } catch (e: Exception) {
                Toast.makeText(
                    this@OfferSwapActivity,
                    "Error creating offer: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.confirmSwapButton.isEnabled = true
                binding.confirmSwapButton.text = "Replacement confirmation"
            }
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        mapManager.setMap(map)

        lifecycleScope.launch {
            val currentLocationResult = LocationUtils.getCurrentLocation(this@OfferSwapActivity)
            val currentLocation = currentLocationResult.getOrNull()

            currentLocation?.let { location ->
                mapManager.updateMapLocation(location)
            } ?: run {
                Toast.makeText(
                    this@OfferSwapActivity,
                    "Unable to locate current location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun performPlaceSearch(query: String) {
        try {
            // Get current location
            val currentLocationResult = LocationUtils.getCurrentLocation(this)
            val currentLocation = currentLocationResult.getOrNull()

            val predictions = mapManager.getPlacePredictions(query)

            val suggestions = predictions.mapNotNull { prediction ->
                try {
                    val place = mapManager.fetchPlaceDetails(prediction.placeId)
                    val displayName = prediction.getPrimaryText(null).toString()
                    val fullAddress = place.formattedAddress ?: prediction.getFullText(null).toString()
                    val location = place.location

                    location?.let { loc ->
                        val placeLatLng = LatLng(loc.latitude, loc.longitude)
                        val distance = currentLocation?.let { current ->
                            LocationUtils.calculateDistance(current, placeLatLng)
                        }

                        PlaceSuggestion(
                            placeId = prediction.placeId,
                            name = displayName,
                            address = fullAddress,
                            city = prediction.getSecondaryText(null).toString().split(",").firstOrNull()?.trim() ?: "",
                            latLng = PlaceSuggestion.LatLng(loc.latitude, loc.longitude),
                            distance = distance
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.distance }

            withContext(Dispatchers.Main) {
                placeSuggestionAdapter.submitList(suggestions)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@OfferSwapActivity,
                    "Place search error ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handlePlaceSelection(place: PlaceSuggestion) {
        lifecycleScope.launch {
            try {
                binding.placeSuggestionsRecyclerView.visibility = View.GONE
                selectedPlace = place

                updateConfirmButtonState()

                // Show the selected location card
                binding.selectedLocationCard.visibility = View.VISIBLE
                binding.selectedLocationName.text = place.name
                binding.selectedLocationAddress.text = place.address

                place.distance?.let { distance ->
                    val formattedDistance = LocationUtils.formatDistance(distance)
                    binding.selectedLocationDistance.text = "distance: $formattedDistance"
                    binding.selectedLocationDistance.visibility = View.VISIBLE
                } ?: run {
                    binding.selectedLocationDistance.visibility = View.GONE
                }

                // Update the search box
                val displayText = "${place.name} - ${place.address}"
                binding.searchLocationInput.setText(displayText)
                binding.searchLocationInput.clearFocus()

                val fetchedPlace = mapManager.fetchPlaceDetails(place.placeId)
                val location = fetchedPlace.location

                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mapManager.updateMapLocation(latLng)
                } else {
                    // Fallback to Geocoding API
                    locationService.getLocationFromAddress(place.address)
                        .onSuccess { latLng ->
                            mapManager.updateMapLocation(latLng)
                        }
                        .onFailure { exception ->
                            Toast.makeText(
                                this@OfferSwapActivity,
                                exception.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@OfferSwapActivity,
                    "Error fetching place details: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadTimeSlots(itemId: String) {
        lifecycleScope.launch {
            itemRepository.getItemTimeSlots(itemId)
                .onSuccess { timeSlots ->
                    updateTimeSlots(timeSlots)
                }
                .onFailure { exception ->
                    Toast.makeText(
                        this@OfferSwapActivity,
                        "Error loading time slots: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun updateTimeSlots(timeSlots: List<String>) {
        val currentTime = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy HH:mm", Locale.getDefault())

        val radioButtons = listOf(
            binding.timeSlot1,
            binding.timeSlot2,
            binding.timeSlot3,
            binding.timeSlot4
        )

        timeSlots.forEachIndexed { index, timeSlot ->
            if (index < radioButtons.size) {
                val dateInMillis = try {
                    dateFormat.parse(timeSlot)?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Log.e("DateParsing", "Failed to parse date: $timeSlot", e)
                    Long.MAX_VALUE
                }

                val isExpired = dateInMillis < currentTime

                radioButtons[index].apply {
                    text = timeSlot
                    isEnabled = !isExpired
                    visibility = View.VISIBLE
                }
            }
        }
        updateConfirmButtonState()
    }


    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
        itemSelectionDialog?.dismiss()
    }
}