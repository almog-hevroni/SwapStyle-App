package com.example.swapstyleproject.data.repository.Notification

import com.example.swapstyleproject.model.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NotificationRepositoryImpl : NotificationRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun getUserNotifications(): Flow<List<Notification>> = flow {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val notifications = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Notification::class.java)
            }

            emit(notifications)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getUnreadNotificationsCount(): Result<Int> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .await()

        snapshot.size()
    }

    override suspend fun markAllNotificationsAsRead(): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .await()

        val batch = firestore.batch()

        snapshot.documents.forEach { doc ->
            val ref = firestore.collection("users")
                .document(userId)
                .collection("notifications")
                .document(doc.id)

            batch.update(ref, "isRead", true)
        }

        batch.commit().await()
    }
}