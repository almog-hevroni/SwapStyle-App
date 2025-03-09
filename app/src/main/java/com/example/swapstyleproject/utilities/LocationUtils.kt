package com.example.swapstyleproject.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await

object LocationUtils {
    suspend fun getCurrentLocation(context: Context): Result<LatLng?> {
        return try {
            // Explicit permission check
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success(null)
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            try {
                val location = fusedLocationClient.lastLocation.await()

                if (location != null) {
                    Result.success(LatLng(location.latitude, location.longitude))
                } else {
                    Result.success(null)
                }
            } catch (securityException: SecurityException) {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission(
        activity: ComponentActivity,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        when {
            hasLocationPermission(activity) -> {
                onPermissionGranted()
            }
            activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show explanation dialog
                AlertDialog.Builder(activity)
                    .setTitle("Location Permission Required")
                    .setMessage("Location permission is needed to show distances to meeting places")
                    .setPositiveButton("OK") { _, _ ->
                        launchPermissionRequest(activity, onPermissionGranted, onPermissionDenied)
                    }
                    .setNegativeButton("Cancel") { _, _ -> onPermissionDenied() }
                    .show()
            }
            else -> {
                // Direct permission request
                launchPermissionRequest(activity, onPermissionGranted, onPermissionDenied)
            }
        }
    }

    private fun launchPermissionRequest(
        activity: ComponentActivity,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        val permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grantResults: Map<String, Boolean> ->
            when {
                grantResults.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        grantResults.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    onPermissionGranted()
                }
                else -> {
                    onPermissionDenied()
                }
            }
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0]
    }

    fun formatDistance(meters: Float): String {
        return when {
            meters < 1000 -> "${meters.toInt()}m"
            else -> String.format("%.1fkm", meters / 1000)
        }
    }

}