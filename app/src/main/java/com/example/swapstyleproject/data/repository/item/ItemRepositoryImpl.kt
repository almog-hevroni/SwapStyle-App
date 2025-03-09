package com.example.swapstyleproject.data.repository.item

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import com.example.swapstyleproject.model.ClothingItem
import com.example.swapstyleproject.model.ItemStatus
import com.example.swapstyleproject.model.SwapOfferStatus
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Locale


class ItemRepositoryImpl : ItemRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private val processedSwapIdsGlobal = mutableSetOf<String>()
    }

    override suspend fun getItemById(itemId: String): Result<ClothingItem> = runCatching {
        val document = firestore.collection("items").document(itemId).get().await()

        if (document.exists()) {
            val currentUserId = auth.currentUser?.uid
            val isFavorite = if (currentUserId != null) {
                val favoriteDoc = firestore.collection("users")
                    .document(currentUserId)
                    .collection("favorites")
                    .document(itemId)
                    .get()
                    .await()
                favoriteDoc.exists()
            } else false

            document.toObject(ClothingItem::class.java)?.copy(
                id = document.id,
                isFavorite = isFavorite,
                isOwnItem = document.getString("userId") == currentUserId
            ) ?: throw Exception("Failed to parse item")
        } else {
            throw Exception("Item not found")
        }
    }

    override fun getItems(category: String?): Flow<List<ClothingItem>> = flow {
        try {
            val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

            // Get base query
            var query = firestore.collection("items")
                .whereNotEqualTo("userId", currentUserId)
                .whereEqualTo("status", "AVAILABLE")

            if (category != null && category != "See All") {
                query = query.whereEqualTo("category",
                    if (category == "Accessories") "Accessory" else category)
            }

            // Get favorites for the current user
            val favoritesSnapshot = firestore.collection("users")
                .document(currentUserId)
                .collection("favorites")
                .get()
                .await()

            val favoriteItemIds = favoritesSnapshot.documents.map { it.id }.toSet()

            // Execute query
            val querySnapshot = query.get().await()

            // Get items that are being offered in pending swap offers
            val sentOffersSnapshot = firestore.collectionGroup("sent_offers")
                .whereEqualTo("status", SwapOfferStatus.PENDING.name)
                .get()
                .await()

            // Items that have already been suggested by users for exchange and are awaiting approval
            val itemsInPendingOffers = sentOffersSnapshot.documents
                .mapNotNull { it.getString("offeredItemId") }
                .toSet()

            val items = querySnapshot.documents.mapNotNull { doc ->
                val itemId = doc.id
                // If the item is already on offer and awaiting exchange approval, it is not displayed.
                if (itemId in itemsInPendingOffers) {
                    return@mapNotNull null
                }

                doc.toObject(ClothingItem::class.java)?.copy(
                    id = itemId,
                    isFavorite = itemId in favoriteItemIds,
                    isOwnItem = doc.getString("userId") == currentUserId
                )
            }

            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun addToFavorites(itemId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .document(itemId)
            .set(mapOf(
                "timestamp" to System.currentTimeMillis(),
                "itemId" to itemId
            ))
            .await()
    }

    override suspend fun removeFromFavorites(itemId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .document(itemId)
            .delete()
            .await()
    }

    override suspend fun getFavoriteItemIds(): Result<Set<String>> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .await()

        snapshot.documents.map { it.id }.toSet()
    }

    override suspend fun createItem(item: ClothingItem): Result<String> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        val newItemRef = firestore.collection("items").document()
        val itemId = newItemRef.id

        val itemData = hashMapOf(
            "userId" to userId,
            "title" to item.title,
            "brand" to item.brand,
            "category" to item.category,
            "size" to item.size,
            "description" to (item.description ?: ""),
            "photos" to item.photos,
            "timeSlots" to item.timeSlots,
            "createdAt" to System.currentTimeMillis(),
            "status" to "AVAILABLE"
        )

        newItemRef.set(itemData).await()

        itemId
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        // Verify ownership and availability
        val item = getItemById(itemId).getOrThrow()
        if (item.userId != userId) {
            throw IllegalStateException("User does not own this item")
        }
        if (item.status != ItemStatus.AVAILABLE) {
            throw IllegalStateException("Only available items can be deleted")
        }

        // Get item photos to delete from Storage
        val photoUrls = item.photos

        // 1. Delete offers where this item is offered by others (in user_offers)
        val userOffersSnapshot = firestore.collection("users")
            .document(userId)
            .collection("user_offers")
            .whereEqualTo("itemId", itemId)
            .get()
            .await()

        // Find all interested users who sent offers for this item
        val interestedUserIds = userOffersSnapshot.documents.mapNotNull { doc ->
            doc.getString("interestedUserId")
        }.distinct()

        // 2. For each interested user, delete the offer from their sent_offers collection
        interestedUserIds.forEach { interestedUserId ->
            val sentOffersToDeleteSnapshot = firestore.collection("users")
                .document(interestedUserId)
                .collection("sent_offers")
                .whereEqualTo("itemId", itemId)
                .get()
                .await()

            sentOffersToDeleteSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
        }

        // 3. Delete all offers in the owner's user_offers collection
        userOffersSnapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }

        // 4. Delete any sent offers by the owner that include this item as offered item
        val ownerSentOffersSnapshot = firestore.collection("users")
            .document(userId)
            .collection("sent_offers")
            .whereEqualTo("offeredItemId", itemId)
            .get()
            .await()

        // Find target users who received these offers
        val targetUserIds = ownerSentOffersSnapshot.documents.mapNotNull { doc ->
            doc.getString("itemOwnerId")
        }.distinct()

        // 5. For each target user, delete the offer from their user_offers collection
        targetUserIds.forEach { targetUserId ->
            val offersToDeleteSnapshot = firestore.collection("users")
                .document(targetUserId)
                .collection("user_offers")
                .whereEqualTo("offeredItemId", itemId)
                .get()
                .await()

            offersToDeleteSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
        }

        // 6. Delete all sent offers where this item was offered
        ownerSentOffersSnapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }

        // 7. Delete the item document itself
        firestore.collection("items")
            .document(itemId)
            .delete()
            .await()

        // 8. Remove from all users' favorites
        val favoritesSnapshot = firestore.collectionGroup("favorites")
            .whereEqualTo("itemId", itemId)
            .get()
            .await()

        favoritesSnapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }

        // 9. Delete photos from Firebase Storage
        val storage = FirebaseStorage.getInstance()
        photoUrls.forEach { photoUrl ->
            try {
                // Get reference from URL and delete
                val storageRef = storage.getReferenceFromUrl(photoUrl)
                storageRef.delete().await()
            } catch (e: Exception) {
                // Continue with other deletions even if one fails
            }
        }
    }

    override suspend fun searchItems(query: String): Result<List<ClothingItem>> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        // Get user's favorite items
        val favoriteIds = getFavoriteItemIds().getOrDefault(emptySet())

        // Search in title, brand, and category
        val querySnapshot = firestore.collection("items")
            .whereNotEqualTo("userId", currentUserId)
            .whereEqualTo("status", "AVAILABLE")
            .get()
            .await()

        // Filter and map results
        querySnapshot.documents.mapNotNull { doc ->
            val item = doc.toObject(ClothingItem::class.java)
            if (item != null &&
                (item.title.contains(query, ignoreCase = true) ||
                        item.brand.contains(query, ignoreCase = true) ||
                        item.category.contains(query, ignoreCase = true) ||
                        item.size.contains(query, ignoreCase = true))
            ) {
                item.copy(
                    id = doc.id,
                    isFavorite = doc.id in favoriteIds,
                    isOwnItem = doc.getString("userId") == currentUserId
                )
            } else null
        }
    }

    override suspend fun updateItemStatus(itemId: String, newStatus: String): Result<Unit> = runCatching {
        firestore.collection("items")
            .document(itemId)
            .update("status", newStatus)
            .await()
    }

    override suspend fun getUserItemsByStatus(status: String): Result<List<ClothingItem>> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        val querySnapshot = firestore.collection("items")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("status", status)
            .get()
            .await()

        querySnapshot.documents.mapNotNull { doc ->
            doc.toObject(ClothingItem::class.java)?.copy(
                id = doc.id,
                isOwnItem = true
            )
        }
    }

    //Update available items whose time windows have all expired
    //Update items that are in the process of being exchanged
    //Update expired exchange offers
    override suspend fun checkAndUpdateExpiredItems(): Result<Unit> = runCatching {
        val currentTime = System.currentTimeMillis()

        // Update available items whose all time windows are Peugeot
        updateExpiredAvailableItems(currentTime)

        // Update items in the process of being replaced
        updateExpiredInProcessItems(currentTime)

        // Update expired swaps offers
        updateExpiredSwapOffers(currentTime)
    }

    private suspend fun updateExpiredAvailableItems(currentTime: Long) {
        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy HH:mm", Locale.getDefault())

        //Takes all available items from the firestore
        val availableSnapshot = firestore.collection("items")
            .whereEqualTo("status", "AVAILABLE")
            .get()
            .await()

        //Goes through each item to check if the swap times have passed
        for (doc in availableSnapshot.documents) {
            val item = doc.toObject(ClothingItem::class.java) ?: continue

            val allDatesExpired = item.timeSlots.all { timeSlot ->
                try {
                    val dateInMillis = dateFormat.parse(timeSlot)?.time ?: Long.MAX_VALUE
                    dateInMillis < currentTime
                } catch (e: Exception) {
                    false
                }
            }

            //For each item if the swap time has passed, updates its status to UNAVAILABLE
            if (allDatesExpired) {
                firestore.collection("items")
                    .document(doc.id)
                    .update("status", "UNAVAILABLE")
                    .await()
            }
        }
    }

    private suspend fun updateExpiredInProcessItems(currentTime: Long) {
        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy HH:mm", Locale.getDefault())

        // Takes all IN PROCESS items from the firestore
        val inProcessSnapshot = firestore.collection("items")
            .whereEqualTo("status", "IN_PROCESS")
            .get()
            .await()

        // Keep track of processed swaps locally within this function execution
        val processedSwapIdsLocal = mutableSetOf<String>()

        // Goes through each item to check if the swap times have passed
        for (doc in inProcessSnapshot.documents) {
            val itemId = doc.id
            val itemOwnerId = doc.getString("userId") ?: continue

            // Check if this item is in a swap as the target item
            val acceptedOffersQuery = firestore.collectionGroup("user_offers")
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("status", "ACCEPTED")
                .get()
                .await()

            // Check if a matching offer is found
            if (!acceptedOffersQuery.isEmpty) {
                val offerDoc = acceptedOffersQuery.documents[0]
                val swapId = offerDoc.getString("swapId") ?: continue

                // Skip if we've already processed this swap globally or locally
                if (swapId in processedSwapIdsGlobal || swapId in processedSwapIdsLocal) continue

                // Add to both sets to prevent duplicate processing
                processedSwapIdsGlobal.add(swapId)
                processedSwapIdsLocal.add(swapId)

                val selectedTimeSlot = offerDoc.getString("selectedTimeSlot") ?: continue
                val interestedUserId = offerDoc.getString("interestedUserId") ?: continue
                val offeredItemId = offerDoc.getString("offeredItemId") ?: continue

                try {
                    val swapTime = dateFormat.parse(selectedTimeSlot)?.time ?: continue

                    if (swapTime < currentTime) {
                        // Update main item status
                        updateItemAndUserAfterSwap(itemId, itemOwnerId, interestedUserId)

                        // Update offered item status WITHOUT incrementing swap counts again
                        firestore.collection("items")
                            .document(offeredItemId)
                            .update("status", "SWAPPED")
                            .await()
                    }
                } catch (e: Exception) {
                    // Handle exception
                }
            }

            // We can remove the second check for offered items since we're already handling both sides
            // of the swap in the code above. This eliminates the duplicate increment.
        }
    }

    private suspend fun updateExpiredSwapOffers(currentTime: Long) {
        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy HH:mm", Locale.getDefault())

        // Search all sub collections of sent_offers
        val sentOffersSnapshot = firestore.collectionGroup("sent_offers")
            .whereEqualTo("status", SwapOfferStatus.PENDING.name)
            .get()
            .await()

        for (doc in sentOffersSnapshot.documents) {
            val selectedTimeSlot = doc.getString("selectedTimeSlot") ?: continue
            val swapId = doc.getString("swapId") ?: continue
            val interestedUserId = doc.getString("interestedUserId") ?: continue
            val itemOwnerId = doc.getString("itemOwnerId") ?: continue

            try {
                val swapTime = dateFormat.parse(selectedTimeSlot)?.time ?: continue

                // If the swap time has passed and the request is still pending
                if (swapTime < currentTime) {
                    updateExpiredSwapOfferStatus(
                        swapId,
                        interestedUserId,
                        itemOwnerId
                    )
                }
            } catch (e: Exception) {
            }
        }
    }

    //Update the status to swapped for both users
    private suspend fun updateItemAndUserAfterSwap(
        itemId: String,
        itemOwnerId: String,
        interestedUserId: String
    ) {
        // Update item status to SWAPPED
        firestore.collection("items")
            .document(itemId)
            .update("status", "SWAPPED")
            .await()

        // Increment swap count for both users
        val userUpdateTasks = listOf(itemOwnerId, interestedUserId).map { userId ->
            firestore.collection("users")
                .document(userId)
                .update("swapCount", FieldValue.increment(1))
        }
        Tasks.whenAll(userUpdateTasks).await()
    }

    private suspend fun updateExpiredSwapOfferStatus(
        swapId: String,
        interestedUserId: String,
        itemOwnerId: String
    ) {
        // Update the offer status to the sending user
        firestore.collection("users")
            .document(interestedUserId)
            .collection("sent_offers")
            .document(swapId)
            .update("status", SwapOfferStatus.REJECTED.name)
            .await()

        // Update the offer status to the item owner
        firestore.collection("users")
            .document(itemOwnerId)
            .collection("user_offers")
            .document(swapId)
            .update("status", SwapOfferStatus.REJECTED.name)
            .await()
    }

    override suspend fun getItemTimeSlots(itemId: String): Result<List<String>> = runCatching {
        val document = firestore.collection("items")
            .document(itemId)
            .get()
            .await()

        if (!document.exists()) {
            throw Exception("Item not found")
        }

        @Suppress("UNCHECKED_CAST")
        (document.get("timeSlots") as? List<String>) ?: emptyList()
    }

    override suspend fun filterItemsNotOffered(items: List<ClothingItem>): List<ClothingItem> {
        val currentUserId = auth.currentUser?.uid ?: return emptyList()

        val sentOffersSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("sent_offers")
            .whereEqualTo("status", SwapOfferStatus.PENDING.name)
            .get()
            .await()

        //filter the items that are already being offered for exchange.
        val offeredItemIds = sentOffersSnapshot.documents
            .mapNotNull { it.getString("offeredItemId") }
            .toSet()

        return items.filter { it.id !in offeredItemIds }
    }

    override suspend fun isItemAvailableForSwap(itemId: String): Result<Boolean> = runCatching {
        val item = getItemById(itemId).getOrThrow()
        item.status == ItemStatus.AVAILABLE
    }

    override fun clearProcessedSwaps() {
        processedSwapIdsGlobal.clear()
    }
}