package com.example.swapstyleproject.model

data class User(
    val id: String,
    val username: String,
    val profileImageUrl: String? = null,
    val swapCount: Int = 0
) {
    val userLevel: String
        get() = when {
            swapCount >= 5 -> "VIP"
            swapCount >= 3 -> "Experienced"
            else -> "Beginner"
        }
}