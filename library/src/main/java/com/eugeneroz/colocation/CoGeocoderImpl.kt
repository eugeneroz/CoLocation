package com.eugeneroz.colocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
internal class CoGeocoderImpl(
    context: Context,
    locale: Locale,
    private val dispatcher: CoroutineDispatcher
) : CoGeocoder {

    private val geocoder: Geocoder by lazy { Geocoder(context, locale) }

    override suspend fun getAddressFromLocation(location: Location, locale: Locale): Address? =
        getAddressListFromLocation(location.latitude, location.longitude, locale, 1).firstOrNull()

    override suspend fun getAddressFromLocation(
        latitude: Double,
        longitude: Double,
        locale: Locale
    ): Address? =
        getAddressListFromLocation(latitude, longitude, locale, 1).firstOrNull()

    override suspend fun getAddressFromLocationName(
        locationName: String,
        locale: Locale
    ): Address? =
        getAddressListFromLocationName(locationName, locale, 1).firstOrNull()

    override suspend fun getAddressFromLocationName(
        locationName: String,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
        locale: Locale
    ): Address? = getAddressListFromLocationName(
        locationName,
        lowerLeftLatitude,
        lowerLeftLongitude,
        upperRightLatitude,
        upperRightLongitude,
        locale,
        1
    ).firstOrNull()

    override suspend fun getAddressListFromLocation(
        location: Location,
        locale: Locale,
        maxResults: Int
    ): List<Address> =
        getAddressListFromLocation(location.latitude, location.longitude, locale, maxResults)

    override suspend fun getAddressListFromLocation(
        latitude: Double,
        longitude: Double,
        locale: Locale,
        maxResults: Int
    ): List<Address> = withContext(dispatcher) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    latitude, longitude, maxResults
                ) { addresses -> cont.resume(addresses) }
            }
        } else {
            geocoder.getFromLocation(latitude, longitude, maxResults) ?: emptyList()
        }
    }

    override suspend fun getAddressListFromLocationName(
        locationName: String,
        locale: Locale,
        maxResults: Int
    ): List<Address> = withContext(dispatcher) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { continuation ->
                geocoder.getFromLocationName(
                    locationName, maxResults
                ) {
                    continuation.resume(value = it)
                }
            }
        } else {
            geocoder.getFromLocationName(locationName, maxResults) ?: emptyList()
        }
    }

    override suspend fun getAddressListFromLocationName(
        locationName: String,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
        locale: Locale,
        maxResults: Int
    ): List<Address> = withContext(dispatcher) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { continuation ->
                geocoder.getFromLocationName(
                    locationName,
                    maxResults,
                    lowerLeftLatitude,
                    lowerLeftLongitude,
                    upperRightLatitude,
                    upperRightLongitude
                ) {
                    continuation.resume(value = it)
                }
            }
        } else {
            geocoder.getFromLocationName(
                locationName,
                maxResults,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            ) ?: emptyList()
        }
    }
}