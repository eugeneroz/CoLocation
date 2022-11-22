package com.eugeneroz.colocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Geocoder.GeocodeListener
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import io.mockk.*
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.TimeUnit

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
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(5, unit = TimeUnit.SECONDS)
class CoGeocoderTest {

    private val context: Context = mockk()

    private val testCoroutineDispatcher = StandardTestDispatcher()
    private val testCoroutineScope = TestScope(testCoroutineDispatcher)

    private val coLocation = CoGeocoderImpl(context, Locale.getDefault(), testCoroutineDispatcher)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(testCoroutineDispatcher)
        mockkConstructor(Geocoder::class)
    }

    @AfterEach
    fun after() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `getAddressFromLocation with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        val address: Address = mockk()

        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns listOf(
            address
        )
        val result = coLocation.getAddressFromLocation(location)

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocation with result`() = runTest {
        mockSdkInt(Build.VERSION_CODES.TIRAMISU)
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        val address: Address = mockk()

        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every {
            anyConstructed<Geocoder>().getFromLocation(
                latitude,
                longitude,
                1,
                any()
            )
        } answers {
            (arg(3) as (GeocodeListener)).onGeocode(listOf(address))
        }
        val result = coLocation.getAddressFromLocation(location)

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocation without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns emptyList()

        val result = coLocation.getAddressFromLocation(location)

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocation without result`() = runTest {
        mockSdkInt(Build.VERSION_CODES.TIRAMISU)
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every {
            anyConstructed<Geocoder>().getFromLocation(
                latitude,
                longitude,
                1,
                any()
            )
        } answers {
            (arg(3) as (GeocodeListener)).onGeocode(emptyList())
        }

        val result = coLocation.getAddressFromLocation(location)

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocation lat lng with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns listOf(
            address
        )

        val result = coLocation.getAddressFromLocation(latitude, longitude)

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocation lat lng without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        every {
            anyConstructed<Geocoder>().getFromLocation(
                latitude,
                longitude,
                1
            )
        } returns emptyList()

        val result = coLocation.getAddressFromLocation(latitude, longitude)

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocationName with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 1) } returns listOf(
            address
        )

        val result = coLocation.getAddressFromLocationName(locationName)

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocationName without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                1
            )
        } returns emptyList()

        val result = coLocation.getAddressFromLocationName(locationName)

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocationName bounds with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        val address: Address = mockk()
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                1,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns listOf(address)

        val result = coLocation.getAddressFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocationName bounds without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                1,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns emptyList()

        val result = coLocation.getAddressFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )

        assertNull(result)
    }

    @Test
    fun `getAddressListFromLocation with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        val address: Address = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns listOf(
            address
        )

        val result = coLocation.getAddressListFromLocation(location, maxResults = 5)

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocation without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every {
            anyConstructed<Geocoder>().getFromLocation(
                latitude,
                longitude,
                5
            )
        } returns emptyList()

        val result = coLocation.getAddressListFromLocation(location, maxResults = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocation lat lng with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns listOf(
            address
        )

        val result = coLocation.getAddressListFromLocation(
                    latitude,
                    longitude,
                    maxResults = 5
                )

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocation lat lng without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val latitude = 1.0
        val longitude = 2.0
        every {
            anyConstructed<Geocoder>().getFromLocation(
                latitude,
                longitude,
                5
            )
        } returns emptyList()

        val result = coLocation.getAddressListFromLocation(
                    latitude,
                    longitude,
                    maxResults = 5
                )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocationName with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 5) } returns listOf(
            address
        )

        val result = coLocation.getAddressListFromLocationName(locationName, maxResults = 5)

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocationName without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                5
            )
        } returns emptyList()

        val result = coLocation.getAddressListFromLocationName(locationName, maxResults = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocationName bounds with result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        val address: Address = mockk()
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                5,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns listOf(address)

        val result = coLocation.getAddressListFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude,
                maxResults = 5
            )

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocationName bounds without result before Tiramisu`() = runTest {
        mockSdkInt(Build.VERSION_CODES.S)
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                5,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns emptyList()

        val result = coLocation.getAddressListFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude,
                maxResults = 5
            )

        assertTrue(result.isEmpty())
    }
}