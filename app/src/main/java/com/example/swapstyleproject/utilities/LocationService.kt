package com.example.swapstyleproject.utilities

import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

//a utility for converting human-readable addresses into geographic coordinates

class LocationService(private val apiKey: String) {

    suspend fun getLocationFromAddress(address: String): Result<LatLng> = withContext(Dispatchers.IO) {
        try {
            val encodedAddress = Uri.encode(address)
            val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encodedAddress&key=$apiKey")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    val response = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }

                    val jsonObject = JSONObject(response.toString())
                    val status = jsonObject.getString("status")

                    if (status == "OK") {
                        val results = jsonObject.getJSONArray("results")
                        if (results.length() > 0) {
                            val location = results.getJSONObject(0)
                                .getJSONObject("geometry")
                                .getJSONObject("location")

                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")

                            Result.success(LatLng(lat, lng))
                        } else {
                            Result.failure(Exception("No location found."))
                        }
                    } else {
                        Result.failure(Exception("Error: $status"))
                    }
                } else {
                    Result.failure(Exception("HTTP Error: $responseCode"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}