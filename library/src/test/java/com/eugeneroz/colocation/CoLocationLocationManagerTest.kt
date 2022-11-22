package com.eugeneroz.colocation

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
class CoLocationLocationManagerTest {
    private val settings: SettingsClient = mockk(relaxed = true)
    private val context: Context = mockk()
    private val locationManager: LocationManager = mockk()
    private val testCoroutineDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testCoroutineDispatcher)
    private val coLocation: CoLocation = CoLocationLocationManagerImpl(context, settings)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(testCoroutineDispatcher)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
    }

    @AfterEach
    fun after() {
        testScope.cancel()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `flushLocations before S`() = runTest {
        mockSdkInt(Build.VERSION_CODES.R)

        assertThrows(java.lang.IllegalStateException::class.java) {
            runBlocking {
                coLocation.flushLocations()
            }
        }
    }

    @Test
    fun `flushLocations`() = runTest {
        mockSdkInt(Build.VERSION_CODES.TIRAMISU)
        val locationListener = mockk<LocationListener>()

        every { locationManager.allProviders } returns listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.FUSED_PROVIDER,
        )

        every { locationManager.requestFlush(any(), any<LocationListener>(), any()) } answers {
            secondArg<LocationListener>().onFlushComplete(thirdArg())
        }

        coLocation.flushLocations()
        verify {
            locationManager.requestFlush(LocationManager.GPS_PROVIDER, any<LocationListener>(), any())
            locationManager.requestFlush(LocationManager.NETWORK_PROVIDER, any<LocationListener>(), any())
            locationManager.requestFlush(LocationManager.PASSIVE_PROVIDER, any<LocationListener>(), any())
            locationManager.requestFlush(LocationManager.FUSED_PROVIDER, any<LocationListener>(), any())
        }
    }

    private suspend fun CapturingSlot<*>.waitForCapture() {
        while (!isCaptured) {
            delay(1)
        }
    }
}