package com.example.swapstyleproject.model

enum class SwapOfferStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

data class SwapLocation(
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class SwapOffer(
    val swapId: String = "",
    val itemId: String = "",
    val itemTitle: String = "",
    val itemPhotoUrls: List<String> = emptyList(),
    val itemOwnerId: String = "",
    val interestedUserId: String = "",
    val offeredItemId: String = "",  // ID of the item offered in exchange
    val offeredItemTitle: String = "",  // Title of the offered item
    val offeredItemPhotoUrls: List<String> = emptyList(),  // Photos of the offered item
    val selectedLocation: SwapLocation = SwapLocation(),
    val selectedTimeSlot: String = "",
    val status: SwapOfferStatus = SwapOfferStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)