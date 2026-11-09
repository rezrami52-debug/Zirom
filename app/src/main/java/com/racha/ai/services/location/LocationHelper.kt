package com.racha.ai.services.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
        withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine { cont ->
                try {
                    val client =
                        LocationServices.getFusedLocationProviderClient(context)

                    client.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    )
                        .addOnSuccessListener { location ->
                            if (!cont.isActive) return@addOnSuccessListener

                            if (location == null) {
                                cont.resume(null)
                                return@addOnSuccessListener
                            }

                            val city = try {
                                val geocoder = Geocoder(
                                    context,
                                    Locale("ar")
                                )

                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(
                                    location.latitude,
                                    location.longitude,
                                    1
                                )

                                addresses?.firstOrNull()?.locality
                                    ?: addresses?.firstOrNull()?.adminArea
                            } catch (_: Exception) {
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
                            if (cont.isActive) {
                                cont.resume(null)
                            }
                        }

                    cont.invokeOnCancellation {
                        // لا حاجة لإلغاء مهمة الموقع هنا؛
                        // المهلة تمنع بقاء الطلب عالقًا.
                    }

                } catch (_: Exception) {
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }
        }
}
