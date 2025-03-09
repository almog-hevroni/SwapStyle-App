package com.example.swapstyleproject.data.repository.swap

import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.SwapOfferStatus
import kotlinx.coroutines.flow.Flow

interface SwapRepository {
    suspend fun createSwapOffer(swapOffer: SwapOffer): Result<String>
    suspend fun getUserSentOffers(): Flow<List<SwapOffer>>
    suspend fun updateSwapOfferStatus(swapId: String, newStatus: SwapOfferStatus): Result<Unit>
    suspend fun getSwapOfferById(swapId: String): Result<SwapOffer>
    suspend fun getItemSwapOffers(itemId: String, status: SwapOfferStatus?): Flow<List<SwapOffer>>
    suspend fun findSwapsByOfferedItem(offeredItemId: String, status: SwapOfferStatus?): Flow<List<SwapOffer>>
}