package com.example.swapstyleproject.data.repository.swap

import android.util.Log
import com.example.swapstyleproject.model.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.SwapLocation
import com.example.swapstyleproject.model.SwapOfferStatus
import java.util.UUID

class SwapRepositoryImpl : SwapRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun createSwapOffer(swapOffer: SwapOffer): Result<String> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        //users/{userId}/swap_offers/{swapId}
        val swapId = UUID.randomUUID().toString()

        val swapOfferData = hashMapOf(
            "swapId" to swapId,
            "itemId" to swapOffer.itemId,
            "itemTitle" to swapOffer.itemTitle,
            "itemPhotoUrls" to swapOffer.itemPhotoUrls,
            "interestedUserId" to currentUserId,
            "itemOwnerId" to swapOffer.itemOwnerId,
            "offeredItemId" to swapOffer.offeredItemId,
            "offeredItemTitle" to swapOffer.offeredItemTitle,
            "offeredItemPhotoUrls" to swapOffer.offeredItemPhotoUrls,
            "selectedLocation" to hashMapOf(
                "name" to swapOffer.selectedLocation.name,
                "address" to swapOffer.selectedLocation.address,
            ),
            "selectedTimeSlot" to swapOffer.selectedTimeSlot,
            "status" to SwapOfferStatus.PENDING.name,
            "createdAt" to System.currentTimeMillis()
        )

        // Save for the proposing user
        firestore.collection("users")
            .document(currentUserId)
            .collection("sent_offers")
            .document(swapId)
            .set(swapOfferData)
            .await()

        // Save for the original owner
        firestore.collection("users")
            .document(swapOffer.itemOwnerId)
            .collection("user_offers")
            .document(swapId)
            .set(swapOfferData)
            .await()

        // Create notification for item owner
        try {
            // Get sender information
            val senderDoc = firestore.collection("users")
                .document(currentUserId)
                .get()
                .await()

            val senderName = senderDoc.getString("username") ?: "Someone"

            // Create notification
            val notificationId = UUID.randomUUID().toString()
            val notificationData = hashMapOf(
                "id" to notificationId,
                "userId" to swapOffer.itemOwnerId,
                "title" to "New Swap Offer",
                "message" to "You received an offer from $senderName for your ${swapOffer.itemTitle} item. Check your profile to see the details.",
                "senderName" to senderName,
                "itemId" to swapOffer.itemId,
                "itemTitle" to swapOffer.itemTitle,
                "swapOfferId" to swapId,
                "type" to "SWAP_OFFER",
                "isRead" to false,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(swapOffer.itemOwnerId)
                .collection("notifications")
                .document(notificationId)
                .set(notificationData)
                .await()
        } catch (e: Exception) {
            // If notification creation fails, we still want to create the swap offer
            // So we just log the error and continue
            e.printStackTrace()
        }

        swapId
    }

    override suspend fun getUserSentOffers(): Flow<List<SwapOffer>> = flow {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("sent_offers")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val offers = mutableListOf<SwapOffer>()

        for (doc in snapshot.documents) {
            try {
                val itemId = doc.getString("itemId") ?: continue

                // Check if the item still exists
                val itemExists = try {
                    val itemDoc = firestore.collection("items").document(itemId).get().await()
                    itemDoc.exists()
                } catch (e: Exception) {
                    false
                }

                // Skip this offer if the item no longer exists
                if (!itemExists) continue

                val locationData = doc.get("selectedLocation") as? Map<*, *>
                val offer = SwapOffer(
                    swapId = doc.getString("swapId") ?: "",
                    itemId = itemId,
                    itemTitle = doc.getString("itemTitle") ?: "",
                    itemPhotoUrls = (doc.get("itemPhotoUrls") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    interestedUserId = doc.getString("interestedUserId") ?: "",
                    itemOwnerId = doc.getString("itemOwnerId") ?: "",
                    offeredItemId = doc.getString("offeredItemId") ?: "",
                    offeredItemTitle = doc.getString("offeredItemTitle") ?: "",
                    offeredItemPhotoUrls = (doc.get("offeredItemPhotoUrls") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    selectedLocation = SwapLocation(
                        name = locationData?.get("name") as? String ?: "",
                        address = locationData?.get("address") as? String ?: "",
                    ),
                    selectedTimeSlot = doc.getString("selectedTimeSlot") ?: "",
                    status = SwapOfferStatus.valueOf(doc.getString("status") ?: SwapOfferStatus.PENDING.name),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )

                offers.add(offer)
            } catch (e: Exception) {
                // Skip this offer if there was an error processing it
                continue
            }
        }
        emit(offers)
    }

    override suspend fun updateSwapOfferStatus(
        swapId: String,
        newStatus: SwapOfferStatus
    ): Result<Unit> = runCatching {
        val currentUserId =
            auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        // First, get the offer from the owner's collection to find details
        val offerDoc = firestore.collection("users")
            .document(currentUserId)
            .collection("user_offers")
            .document(swapId)
            .get()
            .await()

        if (!offerDoc.exists()) {
            throw Exception("Swap offer not found")
        }

        // Get the interested user ID and other details
        val interestedUserId = offerDoc.getString("interestedUserId")
        val itemId = offerDoc.getString("itemId")
        val itemTitle = offerDoc.getString("itemTitle") ?: "your item"
        val offeredItemId = offerDoc.getString("offeredItemId")
        val offeredItemTitle = offerDoc.getString("offeredItemTitle") ?: "their item"

        if (interestedUserId == null || itemId == null) {
            throw Exception("Interested user ID or Item ID not found")
        }

        // If accepting an offer, handle all other offers for this item
        if (newStatus == SwapOfferStatus.ACCEPTED) {
            // Find all other pending offers for this item across all collections
            val ownerOffersQuery = firestore.collectionGroup("user_offers")
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("status", SwapOfferStatus.PENDING.name)
                .whereNotEqualTo("swapId", swapId)

            val ownerOffers = ownerOffersQuery.get().await()

            //Find all other pending offers that include the offered item
            val offeredItemOffers = if (!offeredItemId.isNullOrEmpty()) {
                val offeredItemOffersQuery = firestore.collectionGroup("user_offers")
                    .whereEqualTo("offeredItemId", offeredItemId)
                    .whereEqualTo("status", SwapOfferStatus.PENDING.name)
                    .whereNotEqualTo("swapId", swapId)

                offeredItemOffersQuery.get().await().documents
            } else {
                emptyList()
            }

            // Combine all offers that need to be rejected
            val allOffersToReject = ownerOffers.documents + offeredItemOffers

            // Batch update to reject other offers
            val batch = firestore.batch()

            for (otherOfferDoc in allOffersToReject.distinctBy { it.id }) {
                val otherSwapId = otherOfferDoc.getString("swapId")
                val otherInterestedUserId = otherOfferDoc.getString("interestedUserId")
                val otherItemOwnerId = otherOfferDoc.getString("itemOwnerId")
                val otherItemTitle = otherOfferDoc.getString("itemTitle") ?: "an item"

                if (otherSwapId != null && otherInterestedUserId != null && otherItemOwnerId != null) {
                    // Update in owner's user_offers collection
                    val ownerOfferRef = firestore.collection("users")
                        .document(otherItemOwnerId)
                        .collection("user_offers")
                        .document(otherSwapId)
                    batch.update(ownerOfferRef, "status", SwapOfferStatus.REJECTED.name)

                    // Update in interested user's sent_offers collection
                    val interestedUserOfferRef = firestore.collection("users")
                        .document(otherInterestedUserId)
                        .collection("sent_offers")
                        .document(otherSwapId)
                    batch.update(interestedUserOfferRef, "status", SwapOfferStatus.REJECTED.name)

                    // Create rejection notification for other users
                    try {
                        val notificationId = UUID.randomUUID().toString()

                        val notificationData = hashMapOf(
                            "id" to notificationId,
                            "userId" to otherInterestedUserId,
                            "title" to "Swap Offer Rejected",
                            "message" to "Your offer for $otherItemTitle was automatically rejected because the item has been accepted for another swap.",
                            "senderName" to "",
                            "itemId" to (otherOfferDoc.getString("itemId") ?: ""),
                            "itemTitle" to otherItemTitle,
                            "swapOfferId" to otherSwapId,
                            "type" to NotificationType.SWAP_REJECTED,
                            "isRead" to false,
                            "timestamp" to System.currentTimeMillis()
                        )

                        val notificationRef = firestore.collection("users")
                            .document(otherInterestedUserId)
                            .collection("notifications")
                            .document(notificationId)

                        batch.set(notificationRef, notificationData)
                    } catch (e: Exception) {
                        // Just log the error and continue
                        e.printStackTrace()
                    }
                }
            }

            // Commit the batch update
            batch.commit().await()

            //Update offered item status to IN_PROCESS
            if (!offeredItemId.isNullOrEmpty()) {
                firestore.collection("items")
                    .document(offeredItemId)
                    .update("status", "IN_PROCESS")
                    .await()
            }

            // Update target item status to IN_PROCESS
            firestore.collection("items")
                .document(itemId)
                .update("status", "IN_PROCESS")
                .await()
        }

        // Update the selected offer status in the item owner's collection (user_offers)
        firestore.collection("users")
            .document(currentUserId)
            .collection("user_offers")
            .document(swapId)
            .update("status", newStatus.name)
            .await()

        // Update the selected offer status in the interested user's collection (sent_offers)
        firestore.collection("users")
            .document(interestedUserId)
            .collection("sent_offers")
            .document(swapId)
            .update("status", newStatus.name)
            .await()

        // Create notification for the interested user about acceptance or rejection
        try {
            // Get owner information
            val ownerDoc = firestore.collection("users")
                .document(currentUserId)
                .get()
                .await()

            val ownerName = ownerDoc.getString("username") ?: "The owner"

            val notificationId = UUID.randomUUID().toString()
            val notificationTitle: String
            val notificationMessage: String
            val notificationType: NotificationType

            if (newStatus == SwapOfferStatus.ACCEPTED) {
                notificationTitle = "Swap Offer Accepted"
                notificationMessage = "$ownerName accepted your offer for $itemTitle. You can now proceed with the swap."
                notificationType = NotificationType.SWAP_ACCEPTED
            } else {
                notificationTitle = "Swap Offer Rejected"
                notificationMessage = "$ownerName rejected your offer for $itemTitle."
                notificationType = NotificationType.SWAP_REJECTED
            }

            val notificationData = hashMapOf(
                "id" to notificationId,
                "userId" to interestedUserId,
                "title" to notificationTitle,
                "message" to notificationMessage,
                "senderName" to ownerName,
                "itemId" to itemId,
                "itemTitle" to itemTitle,
                "swapOfferId" to swapId,
                "type" to notificationType,
                "isRead" to false,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(interestedUserId)
                .collection("notifications")
                .document(notificationId)
                .set(notificationData)
                .await()
        } catch (e: Exception) {
            // If notification creation fails, we still want to update the status
            // So we just log the error and continue
            e.printStackTrace()
        }
    }

    override suspend fun getSwapOfferById(swapId: String): Result<SwapOffer> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")

        // Try to find the offer in user_offers collection first (if user is the item owner)
        val ownerOfferQuery = firestore.collection("users")
            .document(currentUserId)
            .collection("user_offers")
            .document(swapId)
            .get()
            .await()

        // If not found in user_offers, try sent_offers (if user is the one who sent the offer)
        val offerDoc = if (ownerOfferQuery.exists()) {
            ownerOfferQuery
        } else {
            firestore.collection("users")
                .document(currentUserId)
                .collection("sent_offers")
                .document(swapId)
                .get()
                .await()
                ?: throw Exception("Swap offer not found")
        }

        if (!offerDoc.exists()) throw Exception("Swap offer not found")

        val locationData = offerDoc.get("selectedLocation") as? Map<*, *>
        SwapOffer(
            swapId = offerDoc.getString("swapId") ?: "",
            // Target item details
            itemId = offerDoc.getString("itemId") ?: "",
            itemTitle = offerDoc.getString("itemTitle") ?: "",
            itemPhotoUrls = (offerDoc.get("itemPhotoUrls") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            interestedUserId = offerDoc.getString("interestedUserId") ?: "",
            itemOwnerId = offerDoc.getString("itemOwnerId") ?: "",
            // Offered item details
            offeredItemId = offerDoc.getString("offeredItemId") ?: "",
            offeredItemTitle = offerDoc.getString("offeredItemTitle") ?: "",
            offeredItemPhotoUrls = (offerDoc.get("offeredItemPhotoUrls") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            // Meeting details
            selectedLocation = SwapLocation(
                name = locationData?.get("name") as? String ?: "",
                address = locationData?.get("address") as? String ?: "",
                latitude = (locationData?.get("latitude") as? Number)?.toDouble() ?: 0.0,
                longitude = (locationData?.get("longitude") as? Number)?.toDouble() ?: 0.0
            ),
            selectedTimeSlot = offerDoc.getString("selectedTimeSlot") ?: "",
            status = SwapOfferStatus.valueOf(offerDoc.getString("status") ?: SwapOfferStatus.PENDING.name),
            createdAt = offerDoc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    override suspend fun getItemSwapOffers(itemId: String, status: SwapOfferStatus?): Flow<List<SwapOffer>> = flow {
        val currentUserId =
            auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        try {
            var query = firestore.collection("users")
                .document(currentUserId)
                .collection("user_offers")
                .whereEqualTo("itemId", itemId)

            if (status != null) {
                query = query.whereEqualTo("status", status.name)
            } else {
                query = query.whereEqualTo("status", SwapOfferStatus.PENDING.name)
            }

            query = query.orderBy("createdAt", Query.Direction.DESCENDING)

            val snapshot = query.get().await()

            val offers = snapshot.documents.mapNotNull { doc ->
                try {
                    val locationData = doc.get("selectedLocation") as? Map<*, *>
                    SwapOffer(
                        swapId = doc.getString("swapId") ?: "",
                        itemId = doc.getString("itemId") ?: "",
                        itemTitle = doc.getString("itemTitle") ?: "",
                        itemPhotoUrls = (doc.get("itemPhotoUrls") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        interestedUserId = doc.getString("interestedUserId") ?: "",
                        itemOwnerId = doc.getString("itemOwnerId") ?: "",
                        offeredItemId = doc.getString("offeredItemId") ?: "",
                        offeredItemTitle = doc.getString("offeredItemTitle") ?: "",
                        offeredItemPhotoUrls = (doc.get("offeredItemPhotoUrls") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        selectedLocation = SwapLocation(
                            name = locationData?.get("name") as? String ?: "",
                            address = locationData?.get("address") as? String ?: "",
                            latitude = (locationData?.get("latitude") as? Number)?.toDouble() ?: 0.0,
                            longitude = (locationData?.get("longitude") as? Number)?.toDouble() ?: 0.0
                        ),
                        selectedTimeSlot = doc.getString("selectedTimeSlot") ?: "",
                        status = SwapOfferStatus.valueOf(
                            doc.getString("status") ?: SwapOfferStatus.PENDING.name
                        ),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            emit(offers)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun findSwapsByOfferedItem(offeredItemId: String, status: SwapOfferStatus?): Flow<List<SwapOffer>> = flow {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
        try {
            var query = firestore.collection("users")
                .document(currentUserId)
                .collection("sent_offers")
                .whereEqualTo("offeredItemId", offeredItemId)

            if (status != null) {
                query = query.whereEqualTo("status", status.name)
            }

            query = query.orderBy("createdAt", Query.Direction.DESCENDING)

            val snapshot = query.get().await()

            val offers = snapshot.documents.mapNotNull { doc ->
                try {
                    val locationData = doc.get("selectedLocation") as? Map<*, *>
                    SwapOffer(
                        swapId = doc.getString("swapId") ?: "",
                        itemId = doc.getString("itemId") ?: "",
                        itemTitle = doc.getString("itemTitle") ?: "",
                        itemPhotoUrls = (doc.get("itemPhotoUrls") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        interestedUserId = doc.getString("interestedUserId") ?: "",
                        itemOwnerId = doc.getString("itemOwnerId") ?: "",
                        offeredItemId = doc.getString("offeredItemId") ?: "",
                        offeredItemTitle = doc.getString("offeredItemTitle") ?: "",
                        offeredItemPhotoUrls = (doc.get("offeredItemPhotoUrls") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        selectedLocation = SwapLocation(
                            name = locationData?.get("name") as? String ?: "",
                            address = locationData?.get("address") as? String ?: "",
                            latitude = (locationData?.get("latitude") as? Number)?.toDouble() ?: 0.0,
                            longitude = (locationData?.get("longitude") as? Number)?.toDouble() ?: 0.0
                        ),
                        selectedTimeSlot = doc.getString("selectedTimeSlot") ?: "",
                        status = SwapOfferStatus.valueOf(
                            doc.getString("status") ?: SwapOfferStatus.PENDING.name
                        ),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            emit(offers)
        } catch (e: Exception) { emit(emptyList())
        }
    }
}