package com.example.swapstyleproject.model

data class ClothingItem(
    val id: String = "",
    val title: String = "",
    val brand: String = "",
    val size: String = "",
    val category: String = "",
    val photos: List<String> = emptyList(),
    val userId: String = "",
    var isFavorite: Boolean = false,
    val isOwnItem: Boolean = false,
    val status: ItemStatus = ItemStatus.AVAILABLE,
    val timeSlots: List<String> = emptyList(),
    val description: String = "",
    val createdAt: Long = 0
)

// Enum for item status
enum class ItemStatus {
    AVAILABLE,   // Item is available for swap
    IN_PROCESS,  // Item is in the process of being swapped
    SWAPPED,     // Item has been successfully swapped
    UNAVAILABLE  // Item is not available for swap
}

