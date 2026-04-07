package com.parentcontrol.child.collector

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.parentcontrol.child.data.model.LocationSyncItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class LocationCollector(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 60_000L): Flow<LocationSyncItem> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val iso = Instant.ofEpochMilli(location.time)
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    val item = LocationSyncItem(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        recordedAt = iso,
                    )
                    trySend(item)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnown(onResult: (LocationSyncItem?) -> Unit) {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val iso = Instant.ofEpochMilli(location.time)
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    onResult(
                        LocationSyncItem(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            speed = if (location.hasSpeed()) location.speed else null,
                            recordedAt = iso,
                        )
                    )
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
}
