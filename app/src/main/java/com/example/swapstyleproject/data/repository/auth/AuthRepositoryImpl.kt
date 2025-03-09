package com.example.swapstyleproject.data.repository.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> = runCatching {
        try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw Exception("Invalid email or password. Please try again.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw Exception("Invalid email or password. Please try again.")
        } catch (e: Exception) {
            throw Exception("Login failed. Please try again.")
        }
    }

    override suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
        username: String
    ): Result<String> = runCatching {
        // First check if username exists
        val usernameSnapshot = firestore.collection("users")
            .whereEqualTo("username", username)
            .get()
            .await()

        if (!usernameSnapshot.isEmpty) {
            throw Exception("Username already exists")
        }

        // Create auth user
        val authResult = auth.createUserWithEmailAndPassword(email, password).await() //Reserved Function-createUserWithEmailAndPassword
        val userId = authResult.user?.uid ?: throw IllegalStateException("User ID not found")

        // Create user document
        val user = mapOf(
            "userId" to userId,
            "username" to username,
            "email" to email,
            "createdAt" to System.currentTimeMillis(),
            "swapCount" to 0,
            "profileImageUrl" to null
        )

        // Save user data to Firestore
        firestore.collection("users")
            .document(userId)
            .set(user)
            .await()

        userId
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        auth.signOut()
    }

    //Auth is a reference to the Firebase Authentication instance
    //Returns true if the user is logged in, false otherwise.
    override fun isUserLoggedIn(): Boolean = auth.currentUser != null


}

