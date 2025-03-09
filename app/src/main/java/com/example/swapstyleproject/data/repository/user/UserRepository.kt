package com.example.swapstyleproject.data.repository.user

import android.net.Uri
import com.example.swapstyleproject.model.User

interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<User>
    suspend fun getCurrentUserProfile(): Result<User>
    suspend fun checkIfUsernameExists(username: String): Result<Boolean>
    suspend fun checkIfEmailExists(email: String): Result<Boolean>
    suspend fun updateUserProfileImage(imageUrl: Uri): Result<Unit>
}