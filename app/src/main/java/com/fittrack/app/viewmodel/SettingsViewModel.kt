package com.fittrack.app.viewmodel

import android.net.Uri
import androidx.lifecycle.*
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.BackupPreferences
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val backupPreferences: BackupPreferences
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    private val _backupFolderUri = MutableStateFlow(backupPreferences.getTreeUri())
    val backupFolderUri: StateFlow<Uri?> = _backupFolderUri.asStateFlow()

    fun save(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        gender: Gender,
        activityLevel: ActivityLevel,
        timerVolumePercent: Int
    ) {
        viewModelScope.launch {
            userPreferences.save(
                UserProfile(
                    weightKg = weightKg,
                    heightCm = heightCm,
                    ageYears = ageYears,
                    gender = gender,
                    activityLevel = activityLevel,
                    timerVolumePercent = timerVolumePercent
                )
            )
        }
    }

    fun saveBackupFolder(uri: Uri) {
        backupPreferences.saveTreeUri(uri)
        _backupFolderUri.value = uri
    }
}

class SettingsViewModelFactory(
    private val userPreferences: UserPreferences,
    private val backupPreferences: BackupPreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(userPreferences, backupPreferences) as T
}
