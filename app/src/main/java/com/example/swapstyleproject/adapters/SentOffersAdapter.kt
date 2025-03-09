package com.example.swapstyleproject.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.swapstyleproject.R
import com.example.swapstyleproject.databinding.ItemSentOfferBinding
import com.example.swapstyleproject.model.SwapOffer
import com.example.swapstyleproject.model.SwapOfferStatus
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.*

//Adapter for sent offers tab in te profile fragment
//is a RecyclerView adapter designed for displaying swap offers that a user has sent to other users.
//Its main purpose is to create and manage the visual representation of sent swap offers in a list format.
class SentOffersAdapter : ListAdapter<SwapOffer, SentOffersAdapter.SentOfferViewHolder>(SentOfferDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SentOfferViewHolder {
        val binding = ItemSentOfferBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SentOfferViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SentOfferViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SentOfferViewHolder(
        private val binding: ItemSentOfferBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(offer: SwapOffer) {

            binding.apply {
                // Set up item images ViewPager
                val imageAdapter = ClothingImageAdapter(offer.itemPhotoUrls)
                itemImageViewPager.adapter = imageAdapter


                // Set up image indicator dots
                TabLayoutMediator(imageIndicator, itemImageViewPager) { tab, _ ->
                    tab.view.minimumWidth = 0  // Ensures proper tab sizing
                }.attach()

                // Show image indicator only if there are multiple images
                imageIndicator.visibility = if (offer.itemPhotoUrls.size > 1) View.VISIBLE else View.GONE

                // Set item title
                itemTitle.text = offer.itemTitle
                locationName.text = offer.selectedLocation.name
                locationAddress.text = offer.selectedLocation.address
                // Set time slot
                timeSlot.text = offer.selectedTimeSlot
                // Set timestamp
                createdAt.text = dateFormat.format(Date(offer.createdAt))

                // טיפול בסטטוס
                statusChip.apply {
                    text = when (offer.status) {
                        SwapOfferStatus.PENDING -> "Waiting for OK"
                        SwapOfferStatus.ACCEPTED -> "Approved"
                        SwapOfferStatus.REJECTED -> "Rejected"
                    }
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, when (offer.status) {
                            SwapOfferStatus.PENDING -> R.color.gray
                            SwapOfferStatus.ACCEPTED -> R.color.green
                            SwapOfferStatus.REJECTED -> R.color.red
                        })
                    )

                    setTextColor(
                        ContextCompat.getColor(context, when (offer.status) {
                            SwapOfferStatus.PENDING -> R.color.black
                            SwapOfferStatus.ACCEPTED, SwapOfferStatus.REJECTED -> R.color.white
                        })
                    )
                }
            }
        }
    }

    private class SentOfferDiffCallback : DiffUtil.ItemCallback<SwapOffer>() {
        override fun areItemsTheSame(oldItem: SwapOffer, newItem: SwapOffer): Boolean {
            return oldItem.swapId == newItem.swapId
        }

        override fun areContentsTheSame(oldItem: SwapOffer, newItem: SwapOffer): Boolean {
            return oldItem == newItem
        }
    }
}