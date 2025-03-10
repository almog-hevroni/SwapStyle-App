package com.example.swapstyleproject.model

data class Notification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val senderName: String = "",
    val itemId: String = "",
    val itemTitle: String = "",
    val swapOfferId: String = "",
    val type: NotificationType = NotificationType.SWAP_OFFER,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    SWAP_OFFER,
    SWAP_ACCEPTED,
    SWAP_REJECTED,
}
