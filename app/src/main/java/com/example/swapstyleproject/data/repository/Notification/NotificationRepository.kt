package com.example.swapstyleproject.data.repository.Notification

import com.example.swapstyleproject.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getUserNotifications(): Flow<List<Notification>>
    suspend fun getUnreadNotificationsCount(): Result<Int>
    suspend fun markAllNotificationsAsRead(): Result<Unit>
}