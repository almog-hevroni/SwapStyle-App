package com.example.swapstyleproject.data.repository.user

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.swapstyleproject.model.User
import com.google.firebase.firestore.FieldValue


class UserRepositoryImpl : UserRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun getCurrentUserProfile(): Result<User> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        getUserProfile(userId).getOrThrow()
    }

    override suspend fun getUserProfile(userId: String): Result<User> = runCatching {
        val document = firestore.collection("users").document(userId).get().await()

        if (document.exists()) {
            User(
                id = userId,
                username = document.getString("username") ?: "",
                profileImageUrl = document.getString("profileImageUrl"),
                swapCount = document.getLong("swapCount")?.toInt() ?: 0
            )
        } else {
            throw Exception("User not found")
        }
    }

    override suspend fun checkIfUsernameExists(username: String): Result<Boolean> = runCatching {
        val snapshot = firestore.collection("users")
            .whereEqualTo("username", username)
            .get()
            .await()
        snapshot.documents.isNotEmpty()
    }

    override suspend fun checkIfEmailExists(email: String): Result<Boolean> = runCatching {
        val snapshot = firestore.collection("users")
            .whereEqualTo("email", email)
            .get()
            .await()
        snapshot.documents.isNotEmpty()
    }

    override suspend fun updateUserProfileImage(imageUrl: Uri): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        firestore.collection("users")
            .document(userId)
            .update("profileImageUrl", imageUrl.toString())
            .await()
    }
}