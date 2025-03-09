package com.example.swapstyleproject.model

data class PlaceSuggestion(
    val placeId: String,
    val name: String,
    val address: String,
    val city: String,
    val latLng: LatLng,
    var distance: Float? = null
) {
    data class LatLng(
        val latitude: Double,
        val longitude: Double
    )
}
