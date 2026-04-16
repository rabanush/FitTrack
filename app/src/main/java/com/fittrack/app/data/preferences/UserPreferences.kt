package com.fittrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

enum class ActivityLevel(val multiplier: Float, val label: String) {
    SEDENTARY(1.2f, "Sitzend (wenig/keine Bewegung)"),
    LIGHT(1.375f, "Leicht aktiv (1–3 Tage/Woche)"),
    MODERATE(1.55f, "Mäßig aktiv (3–5 Tage/Woche)"),
    ACTIVE(1.725f, "Sehr aktiv (6–7 Tage/Woche)"),
    VERY_ACTIVE(1.9f, "Extrem aktiv (körperliche Arbeit)")
}

enum class Gender { MALE, FEMALE }

data class UserProfile(
    val weightKg: Float = 75f,
    val heightCm: Float = 175f,
    val ageYears: Int = 25,
    val gender: Gender = Gender.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val timerVolumePercent: Int = 100
) {
    /**
     * Mifflin-St Jeor BMR:
     *   Men:   (10 × weight) + (6.25 × height) – (5 × age) + 5
     *   Women: (10 × weight) + (6.25 × height) – (5 × age) – 161
     */
    val bmr: Float
        get() {
            val base = 10f * weightKg + 6.25f * heightCm - 5f * ageYears
            return if (gender == Gender.MALE) base + 5f else base - 161f
        }

    /** Total Daily Energy Expenditure = BMR × activity multiplier. */
    val tdee: Float get() = bmr * activityLevel.multiplier
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val WEIGHT = floatPreferencesKey("weight_kg")
        val HEIGHT = floatPreferencesKey("height_cm")
        val AGE = intPreferencesKey("age_years")
        val GENDER = stringPreferencesKey("gender")
        val ACTIVITY = stringPreferencesKey("activity_level")
        val TIMER_VOLUME = intPreferencesKey("timer_volume_percent")
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            weightKg = prefs[Keys.WEIGHT] ?: 75f,
            heightCm = prefs[Keys.HEIGHT] ?: 175f,
            ageYears = prefs[Keys.AGE] ?: 25,
            gender = prefs[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: Gender.MALE,
            activityLevel = prefs[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: ActivityLevel.MODERATE,
            timerVolumePercent = (prefs[Keys.TIMER_VOLUME] ?: 100).coerceIn(0, 100)
        )
    }

    suspend fun save(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT] = profile.weightKg
            prefs[Keys.HEIGHT] = profile.heightCm
            prefs[Keys.AGE] = profile.ageYears
            prefs[Keys.GENDER] = profile.gender.name
            prefs[Keys.ACTIVITY] = profile.activityLevel.name
            prefs[Keys.TIMER_VOLUME] = profile.timerVolumePercent.coerceIn(0, 100)
        }
    }
}
