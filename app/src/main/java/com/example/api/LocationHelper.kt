package com.example.api

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

object LocationHelper {

    @SuppressLint("MissingPermission")
    fun requestLiveLocation(context: Context, callback: (Double, Double, String) -> Unit) {
        if (!hasLocationPermission(context)) {
            callback(0.0, 0.0, "Permission required. Tap 'GPS' to grant location access.")
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        processLocation(context, location, callback)
                    } else {
                        // Try last known location from Fused Location Provider
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                processLocation(context, lastLoc, callback)
                            } else {
                                // Fallback directly to Android System LocationManager for real hardware GPS fix
                                fetchFromLocationManager(context, callback)
                            }
                        }.addOnFailureListener {
                            fetchFromLocationManager(context, callback)
                        }
                    }
                }
                .addOnFailureListener {
                    fetchFromLocationManager(context, callback)
                }
        } catch (e: Throwable) {
            fetchFromLocationManager(context, callback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchFromLocationManager(context: Context, callback: (Double, Double, String) -> Unit) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                callback(0.0, 0.0, "Location hardware unavailable")
                return
            }

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) {
                callback(0.0, 0.0, "GPS is disabled on device. Enable Location in settings.")
                return
            }

            var loc: Location? = null
            if (isGpsEnabled) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (loc == null && isNetworkEnabled) {
                loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (loc != null) {
                processLocation(context, loc, callback)
            } else {
                val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER else null
                if (provider != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        locationManager.getCurrentLocation(
                            provider,
                            null,
                            ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (location != null) {
                                processLocation(context, location, callback)
                            } else {
                                callback(0.0, 0.0, "Acquiring real GPS satellite fix...")
                            }
                        }
                    } else {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                processLocation(context, location, callback)
                                try { locationManager.removeUpdates(this) } catch (e: Exception) {}
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        @Suppress("DEPRECATION")
                        locationManager.requestSingleUpdate(provider, listener, null)
                    }
                } else {
                    callback(0.0, 0.0, "Acquiring real GPS satellite fix...")
                }
            }
        } catch (e: Exception) {
            callback(0.0, 0.0, "Searching for GPS signal...")
        }
    }

    private fun processLocation(context: Context, location: Location, callback: (Double, Double, String) -> Unit) {
        val lat = location.latitude
        val lng = location.longitude
        var resolvedAddress = String.format(Locale.US, "GPS: %.6f, %.6f", lat, lng)
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0].getAddressLine(0)
                        if (!addr.isNullOrBlank()) {
                            resolvedAddress = "$addr (${String.format(Locale.US, "%.5f, %.5f", lat, lng)})"
                        }
                    }
                    callback(lat, lng, resolvedAddress)
                }
                return
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0].getAddressLine(0)
                    if (!addr.isNullOrBlank()) {
                        resolvedAddress = "$addr (${String.format(Locale.US, "%.5f, %.5f", lat, lng)})"
                    }
                }
            }
        } catch (e: Exception) {
            // Geocoder lookup failed or offline — return exact real lat/lng
            resolvedAddress = String.format(Locale.US, "GPS: %.6f, %.6f", lat, lng)
        }
        callback(lat, lng, resolvedAddress)
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
