package com.example.swapstyleproject.data.repository.auth

interface AuthRepository {
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit>
    suspend fun createUserWithEmailAndPassword(email: String, password: String, username: String): Result<String>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    fun isUserLoggedIn(): Boolean
}