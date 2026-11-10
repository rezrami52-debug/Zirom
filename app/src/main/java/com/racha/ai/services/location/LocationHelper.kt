package com.racha.ai.services.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val cityName: String?,
        val governorate: String?,
        val delegation: String?,
        val neighborhood: String?,
        val street: String?,
        val buildingNumber: String?,
        val postalCode: String?,
        val countryName: String?,
        val fullAddress: String?
    )

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LocationData? =
        withTimeoutOrNull(12_000L) {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    val client =
                        LocationServices.getFusedLocationProviderClient(context)

                    client.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    )
                        .addOnSuccessListener { result ->
                            if (cont.isActive) {
                                cont.resume(result)
                            }
                        }
                        .addOnFailureListener {
                            if (cont.isActive) {
                                cont.resume(null)
                            }
                        }

                    cont.invokeOnCancellation {
                        // تمنع المهلة بقاء الطلب معلقًا.
                    }
                } catch (_: Exception) {
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }

            location?.let {
                buildLocationData(context, it)
            }
        }

    private suspend fun buildLocationData(
        context: Context,
        location: Location
    ): LocationData {
        val address = reverseGeocode(
            context = context,
            latitude = location.latitude,
            longitude = location.longitude
        )

        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            cityName = address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea,
            governorate = address?.adminArea,
            delegation = address?.subAdminArea,
            neighborhood = address?.subLocality
                ?: address?.locality,
            street = address?.thoroughfare
                ?: address?.featureName,
            buildingNumber = address?.subThoroughfare,
            postalCode = address?.postalCode,
            countryName = address?.countryName,
            fullAddress = address?.getAddressLine(0)
        )
    }

    private suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): Address? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale("ar"))

            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
