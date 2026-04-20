package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    fun save(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        gender: Gender,
        activityLevel: ActivityLevel,
        timerVolumePercent: Int,
        themeHueDegrees: Float
    ) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                userPreferences.save(
                    UserProfile(
                        weightKg = weightKg,
                        heightCm = heightCm,
                        ageYears = ageYears,
                        gender = gender,
                        activityLevel = activityLevel,
                        timerVolumePercent = timerVolumePercent,
                        themeHueDegrees = themeHueDegrees
                    )
                )
            }
        }
    }
}

class SettingsViewModelFactory(
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(userPreferences) as T
}
