package com.example.swapstyleproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.swapstyleproject.R
import com.example.swapstyleproject.model.PlaceSuggestion
import com.example.swapstyleproject.utilities.LocationUtils

//This is a RecyclerView adapter for displaying place suggestions
//It uses ListAdapter which provides built-in diffing capabilities
//It handles selecting and displaying location suggestions from searches
class PlaceSuggestionAdapter(
    private val onPlaceSelected: (PlaceSuggestion) -> Unit
) : ListAdapter<PlaceSuggestion, PlaceSuggestionAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    private var selectedPlaceId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_suggestion, parent, false)
        return PlaceViewHolder(view, onPlaceSelected,this)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectedPlace(placeId: String) {
        val oldSelectedIndex = currentList.indexOfFirst { it.placeId == selectedPlaceId }
        val newSelectedIndex = currentList.indexOfFirst { it.placeId == placeId }

        selectedPlaceId = placeId

        if (oldSelectedIndex != -1) {
            notifyItemChanged(oldSelectedIndex)
        }
        if (newSelectedIndex != -1) {
            notifyItemChanged(newSelectedIndex)
        }
    }

    class PlaceViewHolder(
        itemView: View,
        private val onPlaceSelected: (PlaceSuggestion) -> Unit,
        private val adapter: PlaceSuggestionAdapter
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.placeTitle)
        private val addressTextView: TextView = itemView.findViewById(R.id.placeAddress)
        private val distanceTextView: TextView = itemView.findViewById(R.id.placeDistance)

        fun bind(place: PlaceSuggestion) {
            // Display place name as title
            nameTextView.text = place.name
            // Display full address as subtitle
            addressTextView.text = place.address

            itemView.isSelected = place.placeId == adapter.selectedPlaceId

            // Show distance if available
            place.distance?.let { distance ->
                distanceTextView.visibility = View.VISIBLE
                distanceTextView.text = LocationUtils.formatDistance(distance)
            } ?: run {
                distanceTextView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                adapter.setSelectedPlace(place.placeId)
                onPlaceSelected(place.copy(
                    address = place.address
                ))
            }
        }
    }

    private class PlaceDiffCallback : DiffUtil.ItemCallback<PlaceSuggestion>() {
        override fun areItemsTheSame(oldItem: PlaceSuggestion, newItem: PlaceSuggestion): Boolean {
            return oldItem.placeId == newItem.placeId
        }

        override fun areContentsTheSame(oldItem: PlaceSuggestion, newItem: PlaceSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}