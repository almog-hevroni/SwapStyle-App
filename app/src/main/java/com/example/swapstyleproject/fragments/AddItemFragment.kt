package com.example.swapstyleproject.ui.add

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.swapstyleproject.R
import com.example.swapstyleproject.databinding.FragmentAddItemBinding
import com.example.swapstyleproject.utilities.BackgroundManager
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.*
import android.view.ContextThemeWrapper
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.model.ClothingItem
import com.example.swapstyleproject.utilities.ImagePickerHelper
import com.example.swapstyleproject.utilities.AnimationHelper
import kotlinx.coroutines.launch
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward

class AddItemFragment : Fragment() {
    private var _binding: FragmentAddItemBinding? = null
    private val binding get() = _binding!!

    private val repository = FirebaseRepository.getInstance()
    private val itemRepository = repository.itemRepository
    private lateinit var imagePickerHelper: ImagePickerHelper

    private val selectedPhotos = mutableListOf<Uri?>(null, null, null)
    private val localUriMap = mutableMapOf<Uri, Uri>()
    private val selectedTimeSlots = mutableListOf<String>()
    private var currentPhotoIndex = 0
    private val MAX_TIME_SLOTS = 4

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
    }

    private fun setupViews() {
        // Load background
        BackgroundManager.loadBackground(
            "background_2.png",
            binding.backgroundImage
        ) {
            Toast.makeText(context, "Failed to load background", Toast.LENGTH_SHORT).show()
        }

        setupToolbar()
        setupImagePicker()
        setupPhotoSelection()
        setupCategoryAndSizeSelection()
        setupTimeSlotSelection()
        setupSubmitButton()
        setupKeyboardHandling()
    }

    //Setting the top toolbar and back button
    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_home)
        }
    }

    //Setting up the image selection system
    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(
            fragment = this,
            onImageUploaded = { uploadedUri, _ ->
                val localUri = selectedPhotos[currentPhotoIndex]
                if (localUri != null) {
                    localUriMap[localUri] = uploadedUri
                }
                selectedPhotos[currentPhotoIndex] = uploadedUri
                updatePhotoPreview(currentPhotoIndex, uploadedUri)
            },
            uploadType = ImagePickerHelper.ImageUploadType.ITEM
        )
    }

    //Setting up a system for recording clicks on the image slots and activating the image selector
    private fun setupPhotoSelection() {
        val photoClickListener = { index: Int ->
            currentPhotoIndex = index
            imagePickerHelper.uploadType = ImagePickerHelper.ImageUploadType.ITEM
            imagePickerHelper.requestPermissionsAndShowDialog(requireContext())
        }

        binding.photoSlot1.setOnClickListener { photoClickListener(0) }
        binding.photoSlot2.setOnClickListener { photoClickListener(1) }
        binding.photoSlot3.setOnClickListener { photoClickListener(2) }
    }


    //Update the display of the selected image in the corresponding slot
    private fun updatePhotoPreview(index: Int, uri: Uri) {
        val imageView = when (index) {
            0 -> binding.photoSlot1
            1 -> binding.photoSlot2
            2 -> binding.photoSlot3
            else -> return
        }

        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(imageView)
    }

    //Setting the category and measure selection
    private fun setupCategoryAndSizeSelection() {
        val categories = arrayOf("Women", "Men", "Kids", "Accessory")
        val categoryAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_menu_popup_item, categories)
        (binding.categoryInput as? AutoCompleteTextView)?.setAdapter(categoryAdapter)

        (binding.categoryInput as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            setupSizeSelection(selectedCategory)
        }
    }

    //Setting size selection options according to the selected category
    private fun setupSizeSelection(category: String) {
        if (category == "Accessory") {
            binding.sizeInput.visibility = View.GONE
            return
        }

        binding.sizeInput.visibility = View.VISIBLE
        binding.sizeInput.setOnClickListener {
            showItemTypeDialog(category)
        }
    }

    //Display a dialog to select the item type (clothing or shoes)
    private fun showItemTypeDialog(category: String) {
        val itemTypes = when (category) {
            "Women", "Men", "Kids" -> arrayOf("Clothing Item", "Shoes")
            else -> arrayOf("Clothing Item")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Select Item Type")
            .setItems(itemTypes) { _, which ->
                when (itemTypes[which]) {
                    "Clothing Item" -> showSizeSelectionDialog(getSizesForCategory(category))
                    "Shoes" -> showSizeSelectionDialog(getShoeSizesForCategory(category))
                }
            }
            .show()
    }

    private fun getSizesForCategory(category: String): Array<String> = when (category) {
        "Women", "Men" -> arrayOf("XS", "S", "M", "L", "XL", "XXL", "XXXL", "Other")
        "Kids" -> arrayOf("Age 2-4", "Age 4-6", "Age 6-8", "Age 8-10", "Other")
        else -> arrayOf("One Size", "Other")
    }

    private fun getShoeSizesForCategory(category: String): Array<String> = when (category) {
        "Women", "Men" -> (37..45).map { it.toString() }.toTypedArray() + "Other"
        "Kids" -> (31..36).map { it.toString() }.toTypedArray() + "Other"
        else -> arrayOf("Other")
    }

    //Display a dialog for selecting a specific size
    private fun showSizeSelectionDialog(sizes: Array<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Select Size")
            .setItems(sizes) { _, which ->
                binding.sizeInput.setText(sizes[which])

                if (sizes[which] == "Other") {
                    showCustomSizeInputDialog()
                }
            }
            .show()
    }

    //Display a dialog for entering a custom size
    private fun showCustomSizeInputDialog() {
        val customSizeInput = EditText(requireContext())
        customSizeInput.hint = "Enter custom size"

        AlertDialog.Builder(requireContext())
            .setTitle("Custom Size")
            .setView(customSizeInput)
            .setPositiveButton("OK") { _, _ ->
                val customSize = customSizeInput.text.toString()
                if (customSize.isNotBlank()) {
                    binding.sizeInput.setText(customSize)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    //Setting up a system for selecting meeting time windows
    private fun setupTimeSlotSelection() {
        binding.addTimeSlotButton.setOnClickListener {
            if (selectedTimeSlots.size >= MAX_TIME_SLOTS) {
                Toast.makeText(context, "Maximum 4 time slots allowed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDateTimePicker()
        }
    }

    //Show the date and time picker
    private fun showDateTimePicker() {
        // Create date picker with constraints for minimum date
        val constraintsBuilder = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setTheme(R.style.CustomCalendarTheme)
            // Set minimum date to today
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .setPositiveButtonText("OK")
            .setNegativeButtonText("Cancel")
            .build()

        constraintsBuilder.addOnPositiveButtonClickListener { dateInMillis ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = dateInMillis
            }

            // Current time to compare with
            val currentCalendar = Calendar.getInstance()

            // Create TimePickerDialog
            val timePickerDialog = TimePickerDialog(
                ContextThemeWrapper(requireContext(), R.style.CustomTimePickerTheme),
                { _, hourOfDay, minute ->
                    // Update selected calendar with hour and minute
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)

                    // Check if selected time is in the past
                    if (calendar.before(currentCalendar) &&
                        calendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(Calendar.DAY_OF_YEAR) &&
                        calendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)) {
                        // If it's today but the time is in the past
                        Toast.makeText(requireContext(), "Please select a future time", Toast.LENGTH_SHORT).show()
                    } else {
                        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy HH:mm", Locale.getDefault())
                        val timeSlot = dateFormat.format(calendar.time)
                        addTimeSlotChip(timeSlot)
                        binding.addTimeSlotButton.isEnabled = selectedTimeSlots.size < MAX_TIME_SLOTS
                    }
                },
                currentCalendar.get(Calendar.HOUR_OF_DAY),
                currentCalendar.get(Calendar.MINUTE),
                false
            )

            timePickerDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK") { _, _ -> }
            timePickerDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel") { dialog, _ ->
                dialog.cancel()
            }

            timePickerDialog.show()

            timePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.tool_bar_color))
                isAllCaps = false
            }
            timePickerDialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.tool_bar_color))
                isAllCaps = false
            }
        }

        constraintsBuilder.show(parentFragmentManager, "DATE_PICKER")
    }

    //Adding a chip that displays the selected time window
    private fun addTimeSlotChip(timeSlot: String) {
        val chip = Chip(requireContext()).apply {
            text = timeSlot
            isCloseIconVisible = true
            setChipBackgroundColorResource(R.color.white)
            setOnCloseIconClickListener {
                binding.timeOptionsChipGroup.removeView(this)
                selectedTimeSlots.remove(timeSlot)
                binding.addTimeSlotButton.isEnabled = true
                binding.addTimeSlotButton.visibility = View.VISIBLE
            }
        }
        binding.timeOptionsChipGroup.addView(chip)
        selectedTimeSlots.add(timeSlot)

        // Checking if we have reached the maximum possible chips
        if (selectedTimeSlots.size >= MAX_TIME_SLOTS) {
            // Hiding the button
            binding.addTimeSlotButton.visibility = View.GONE
        }

    }

    //Setting the send button
    private fun setupSubmitButton() {
        binding.submitButton.setOnClickListener {
            if (validateInput()) {
                createItem()
            }
        }
    }

    //checks that all required fields have been filled in correctly
    private fun validateInput(): Boolean {
        var isValid = true

        if (selectedPhotos.all { it == null }) {
            Toast.makeText(context, "Please select at least one photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (binding.titleInput.text.isNullOrBlank()) {
            binding.titleInput.error = "Title is required"
            isValid = false
        }

        if (binding.brandInput.text.isNullOrBlank()) {
            binding.brandInput.error = "Brand is required"
            isValid = false
        }

        if (binding.categoryInput.text.isNullOrBlank()) {
            binding.categoryInput.error = "Category is required"
            isValid = false
        }

        val selectedCategory = binding.categoryInput.text.toString()
        if (selectedCategory != "Accessory" && binding.sizeInput.text.isNullOrBlank()) {
            binding.sizeInput.error = "Size is required"
            isValid = false
        }

        if (selectedTimeSlots.size != MAX_TIME_SLOTS) {
            Toast.makeText(context, "Please add exactly 4 time options", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    //Before saving the item, the images are uploaded to Storage
    private fun createItem() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)

                // Convert URIs to string addresses
                val photoUrls = selectedPhotos.mapNotNull { uri ->
                    uri?.let {
                        // If there is a local URI, return the remote URI
                        localUriMap[it]?.toString() ?: it.toString()
                    }
                }

                // Check if there are images
                if (photoUrls.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Please select at least one photo",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val item = ClothingItem(
                    title = binding.titleInput.text.toString().trim(),
                    brand = binding.brandInput.text.toString().trim(),
                    category = binding.categoryInput.text.toString().trim(),
                    size = binding.sizeInput.text.toString().trim(),
                    description = binding.descriptionInput.text.toString().trim(),
                    photos = photoUrls,
                    timeSlots = selectedTimeSlots
                )

                itemRepository.createItem(item)
                    .onSuccess {
                        context?.let { ctx ->
                            AnimationHelper.showSuccessAnimation(
                                ctx,
                                "Item added successfully!",
                                onAnimationEnd = {
                                    clearForm()
                                    findNavController().navigateUp()
                                }
                            )
                        }
                    }
                    .onFailure { exception ->
                        Toast.makeText(
                            context,
                            "Failed to add item: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun clearForm() {
        binding.titleInput.text?.clear()
        binding.brandInput.text?.clear()
        binding.sizeInput.text?.clear()
        binding.descriptionInput.text?.clear()
        binding.timeOptionsChipGroup.removeAllViews()
        selectedTimeSlots.clear()

        selectedPhotos.fill(null)
        binding.photoSlot1.setImageResource(R.drawable.ic_add_photo)
        binding.photoSlot2.setImageResource(R.drawable.ic_add_photo)
        binding.photoSlot3.setImageResource(R.drawable.ic_add_photo)

        binding.addTimeSlotButton.isEnabled = true
    }

    private fun setupKeyboardHandling() {
        // Close keyboard by clicking on the background
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        // Close keyboard by clicking the toolbar
        binding.toolbar.setOnClickListener {
            hideKeyboard()
        }

        // Close keyboard by clicking on the title
        binding.toolbar.findViewById<TextView>(R.id.titleTextView)?.setOnClickListener {
            hideKeyboard()
        }

        // Close keyboard by pressing Enter
        val onEditorActionListener = TextView.OnEditorActionListener { textView, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard()
                true
            } else false
        }

        // Adding the listener to all the ceremony fields
        binding.titleInput.setOnEditorActionListener(onEditorActionListener)
        binding.brandInput.setOnEditorActionListener(onEditorActionListener)
        binding.sizeInput.setOnEditorActionListener(onEditorActionListener)
        binding.descriptionInput.setOnEditorActionListener(onEditorActionListener)
    }

    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requireActivity().currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.submitButton.isEnabled = !show
        binding.submitButton.text = if (show) "Adding Item..." else "Submit"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}