package com.example.swapstyleproject.utilities

import android.content.Context
import com.example.swapstyleproject.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume

class MapManager(val context: Context) {
    private var googleMap: GoogleMap? = null
    private var placesClient: PlacesClient

    init {
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(context)
    }

    //Sets up the initial map configuration
    fun setMap(map: GoogleMap) {
        googleMap = map
        setupMap()
    }

    private fun setupMap() {
        // Set default location (Tel Aviv)
        val telAviv = LatLng(32.0853, 34.7818)
        googleMap?.apply {
            moveCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 12f))
            uiSettings.apply {
                isZoomControlsEnabled = true
                isMyLocationButtonEnabled = true
                isMapToolbarEnabled = false
            }
        }
    }

    suspend fun getPlacePredictions(query: String): List<AutocompletePrediction> =
        suspendCancellableCoroutine { continuation ->
            if (query.isEmpty()) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val token = AutocompleteSessionToken.newInstance()

            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setCountries("IL") // Limit to Israel
                .setSessionToken(token)
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    if (continuation.isActive) {
                        continuation.resume(response?.autocompletePredictions ?: emptyList())
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        }

    //Retrieves detailed information about a specific place using its place ID
    suspend fun fetchPlaceDetails(placeId: String): Place = suspendCancellableCoroutine { continuation ->
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.ADDRESS_COMPONENTS,
            Place.Field.LOCATION,
            Place.Field.DISPLAY_NAME,
            Place.Field.PLUS_CODE,
            Place.Field.VIEWPORT,
            Place.Field.TYPES
        )

        val request = FetchPlaceRequest.builder(placeId, placeFields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                if (continuation.isActive) {
                    continuation.resume(response.place)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
    }

    fun updateMapLocation(latLng: LatLng) {
        googleMap?.apply {
            clear() // Remove existing markers
            addMarker(MarkerOptions().position(latLng))
            animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }
}