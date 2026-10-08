package com.racha.ai.services.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val cityName: String?
    )

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LocationData? =
        suspendCancellableCoroutine { cont ->
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location == null) {
                            cont.resume(null)
                            return@addOnSuccessListener
                        }
                        val city = try {
                            val geocoder = Geocoder(context, Locale("ar"))
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(
                                location.latitude,
                                location.longitude,
                                1
                            )
                            addresses?.firstOrNull()?.locality
                                ?: addresses?.firstOrNull()?.adminArea
                        } catch (e: Exception) {
                            null
                        }
                        cont.resume(
                            LocationData(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                cityName = city
                            )
                        )
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }
}
