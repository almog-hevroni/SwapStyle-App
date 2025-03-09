package com.example.swapstyleproject.data.repository.swap

import android.util.Log
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

        // Get the interested user ID
        val interestedUserId = offerDoc.getString("interestedUserId")
        val itemId = offerDoc.getString("itemId")
        val offeredItemId = offerDoc.getString("offeredItemId")

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

            // Find suggestions the user has made with this item to others
            val userSentOffersWithThisItem = if (!offeredItemId.isNullOrEmpty()) {
                val userSentOffersQuery = firestore.collectionGroup("sent_offers")
                    .whereEqualTo("offeredItemId", offeredItemId)
                    .whereEqualTo("status", SwapOfferStatus.PENDING.name)
                    .whereNotEqualTo("swapId", swapId)

                userSentOffersQuery.get().await().documents
            } else {
                emptyList()
            }

            // Combine all offers that need to be rejected
            val allOffersToReject = ownerOffers.documents + offeredItemOffers + userSentOffersWithThisItem

            // Batch update to reject other offers
            val batch = firestore.batch()

            for (otherOfferDoc in allOffersToReject.distinctBy { it.id }) {
                val otherSwapId = otherOfferDoc.getString("swapId")
                val otherInterestedUserId = otherOfferDoc.getString("interestedUserId")
                val otherItemOwnerId = otherOfferDoc.getString("itemOwnerId")

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