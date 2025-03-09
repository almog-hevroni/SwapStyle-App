package com.example.swapstyleproject.data.repository.item

import com.example.swapstyleproject.model.ClothingItem
import kotlinx.coroutines.flow.Flow

interface ItemRepository {
    suspend fun getItemById(itemId: String): Result<ClothingItem>
    fun getItems(category: String? = null): Flow<List<ClothingItem>>
    suspend fun addToFavorites(itemId: String): Result<Unit>
    suspend fun removeFromFavorites(itemId: String): Result<Unit>
    suspend fun getFavoriteItemIds(): Result<Set<String>>
    suspend fun createItem(item: ClothingItem): Result<String>
    suspend fun deleteItem(itemId: String): Result<Unit>
    suspend fun searchItems(query: String): Result<List<ClothingItem>>
    suspend fun updateItemStatus(itemId: String, newStatus: String): Result<Unit>
    suspend fun getItemTimeSlots(itemId: String): Result<List<String>>
    suspend fun checkAndUpdateExpiredItems(): Result<Unit>
    suspend fun getUserItemsByStatus(status: String): Result<List<ClothingItem>>
    suspend fun filterItemsNotOffered(items: List<ClothingItem>): List<ClothingItem>
    suspend fun isItemAvailableForSwap(itemId: String): Result<Boolean>
    fun clearProcessedSwaps()
}
