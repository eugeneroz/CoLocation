package com.eugeneroz.colocationsample

import android.annotation.SuppressLint
import android.app.Application
import android.location.Address
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.SavedStateRegistryOwner
import com.eugeneroz.colocation.CoGeocoder
import com.eugeneroz.colocation.CoLocation
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
class MainViewModel(
    private val coLocation: CoLocation,
    private val coGeocoder: CoGeocoder,
) : ViewModel(), DefaultLifecycleObserver {

    private val locationRequest: LocationRequest = LocationRequest.create()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setInterval(5000)


    private val mutableLocationUpdates: MutableLiveData<Location> = MutableLiveData()
    val locationUpdates: LiveData<Location> = mutableLocationUpdates
    val addressUpdates: LiveData<Address?> = locationUpdates.switchMap { location ->
        liveData { emit(coGeocoder.getAddressFromLocation(location)) }
    }

    private val mutableResolveSettingsEvent: MutableLiveData<CoLocation.SettingsResult.Resolvable> = MutableLiveData()
    val resolveSettingsEvent: LiveData<CoLocation.SettingsResult.Resolvable> = mutableResolveSettingsEvent

    private var locationUpdatesJob: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        startLocationUpdatesAfterCheck()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        locationUpdatesJob?.cancel()
        locationUpdatesJob = null
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesAfterCheck() {
        viewModelScope.launch {
            when (val settingsResult = coLocation.checkLocationSettings(locationRequest)) {
                CoLocation.SettingsResult.Satisfied -> {
                    coLocation.getLastLocation()?.run(mutableLocationUpdates::postValue)
                    startLocationUpdates()
                }
                is CoLocation.SettingsResult.Resolvable -> mutableResolveSettingsEvent.postValue(settingsResult)
                else -> { /* Ignore for now, we can't resolve this anyway */ }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = viewModelScope.launch {
            try {
                coLocation.getLocationUpdates(locationRequest).collect { location ->
                    Log.d("MainViewModel", "Location update received: $location")
                    mutableLocationUpdates.postValue(location)
                }
            } catch (e: CancellationException) {
                Log.e("MainViewModel", "Location updates cancelled", e)
            }
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            owner: SavedStateRegistryOwner,
            defaultArgs: Bundle? = null
        ): AbstractSavedStateViewModelFactory = object : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                savedStateHandle: SavedStateHandle
            ): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    CoLocation.from(application, useFusedLocation = false),
                    CoGeocoder.from(application)) as T
            }

        }

        @Suppress("UNCHECKED_CAST")
        val Factory : ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
            extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY])

                return MainViewModel(
                    CoLocation.from(application, useFusedLocation = false),
                    CoGeocoder.from(application)
                ) as T
            }
        }
    }
}