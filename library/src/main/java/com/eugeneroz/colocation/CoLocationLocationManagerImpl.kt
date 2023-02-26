package com.eugeneroz.colocation

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationListenerCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/* Copyright 2022 Eugene Rozenberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
internal class CoLocationLocationManagerImpl(private val context: Context,
                                             private val settings: SettingsClient) : CoLocation {
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val cancelledMessage = "Task was cancelled"

    override suspend fun flushLocations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            suspendCancellableCoroutine<Unit> { cont ->
                val locationListener = object : LocationListenerCompat {
                    override fun onLocationChanged(location: Location) {}
                    override fun onFlushComplete(requestCode: Int) {
                        if (locationManager.allProviders.size - 1 == requestCode) {
                            cont.resume(Unit)
                        }
                    }
                }
                locationManager.allProviders.forEachIndexed { index, provider ->
                    locationManager.requestFlush(
                        provider,
                        locationListener,
                        index
                    )
                }
            }
        } else {
            throw IllegalStateException("Unavailable before SDK ${Build.VERSION_CODES.S}")
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun isLocationAvailable(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getCurrentLocation(priority: Int): Location? =
        suspendCancellableCoroutine { cont ->
            val cancellationTokenSource = CancellationTokenSource()

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0f
            ) { location -> cont.resume(location) }

            cont.invokeOnCancellation { cancellationTokenSource.cancel() }
        }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getLastLocation(): Location? =
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getLocationUpdate(locationRequest: LocationRequest): Location =
        suspendCancellableCoroutine { cont ->
            lateinit var callback: ClearableLocationListener
            callback = LocationListenerCompat { location ->
                cont.resume(location)
                locationManager.removeUpdates(callback)
                callback.clear()
            }.let(::ClearableLocationListener) // Needed since we would have memory leaks otherwise

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                locationRequest.intervalMillis,
                0f,
                callback
            )
        }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override fun getLocationUpdates(
        locationRequest: LocationRequest,
        capacity: Int
    ): Flow<Location> =
        callbackFlow {
            val callback = object : LocationListenerCompat {
                private var counter: Int = 0

                override fun onLocationChanged(location: Location) {
                    trySendBlocking(location)
                    if (locationRequest.maxUpdates == ++counter) {
                        close()
                    }
                }

                override fun onProviderEnabled(provider: String) {
                }

                override fun onProviderDisabled(provider: String) {
                }
            }.let(::ClearableLocationListener) // Needed since we would have memory leaks otherwise

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                locationRequest.intervalMillis,
                0f,
                callback,
                Looper.getMainLooper()
            )

            awaitClose {
                locationManager.removeUpdates(callback)
                callback.clear()
            }
        }.buffer(capacity)

    override suspend fun checkLocationSettings(locationSettingsRequest: LocationSettingsRequest): CoLocation.SettingsResult =
        suspendCancellableCoroutine { cont ->
            settings.checkLocationSettings(locationSettingsRequest)
                .addOnCompleteListener {
                    try {
                        it.getResult(ApiException::class.java)
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        cont.resume(CoLocation.SettingsResult.Satisfied)
                    } catch (exception: ApiException) {
                        Log.e("Colocation","checkLocationSettings", exception)
                        when (exception.statusCode) {
                            LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->
                            // Location settings are not satisfied. But could be fixed by showing the
                            // user a dialog.
                            try {
                                // Cast to a resolvable exception.
                                val resolvable = exception as ResolvableApiException
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                cont.resume(CoLocation.SettingsResult.Resolvable(resolvable))
                            } catch (e: IntentSender.SendIntentException) {
                                // Ignore the error.
                            } catch (e: ClassCastException) {
                                // Ignore, should be an impossible error.
                            }
                            LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                                // Location settings are not satisfied. However, we have no way to fix the
                                // settings so we won't show the dialog.
                                cont.resume(CoLocation.SettingsResult.NotResolvable(exception))
                            }
                        }
                    }
                }
                .addOnCanceledListener {
                    cont.resumeWithException(
                        TaskCancelledException(
                            cancelledMessage
                        )
                    )
                }
        }

    override suspend fun checkLocationSettings(locationRequest: LocationRequest): CoLocation.SettingsResult =
        checkLocationSettings(
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        )

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun setMockLocation(location: Location) {
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun setMockMode(isMockMode: Boolean) {
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, isMockMode)
    }
}

/** Wraps [listener] so that the reference can be cleared */
private class ClearableLocationListener(listener: LocationListenerCompat) : LocationListenerCompat {
    private var listener: LocationListener? = listener

    override fun onLocationChanged(location: Location) {
        listener?.onLocationChanged(location)
    }

    override fun onProviderEnabled(provider: String) {
        listener?.onProviderEnabled(provider)
    }

    override fun onProviderDisabled(provider: String) {
        listener?.onProviderDisabled(provider)
    }

    fun clear() {
        listener = null
    }

}