package com.fittrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fittrack.app.util.normalizeHueDegrees
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")
const val DEFAULT_THEME_HUE_DEGREES = 340f

enum class ActivityLevel(val multiplier: Float, val label: String) {
    SEDENTARY(1.2f, "Keine Bewegung (fast nur Sitzen)"),
    LIGHT(1.375f, "Wenig aktiv (ein bisschen Bewegung im Alltag)"),
    MODERATE(1.55f, "Alltag aktiv (viel Gehen/Stehen)"),
    ACTIVE(1.725f, "Sehr aktiv (regelmäßig Sport + aktiver Alltag)"),
    VERY_ACTIVE(1.9f, "Aktive körperliche Arbeit (sehr anstrengend)")
}

enum class Gender { MALE, FEMALE }

data class UserProfile(
    val weightKg: Float = 75f,
    val heightCm: Float = 175f,
    val ageYears: Int = 25,
    val gender: Gender = Gender.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val timerVolumePercent: Int = 50,
    val themeHueDegrees: Float = DEFAULT_THEME_HUE_DEGREES
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
        val THEME_HUE_DEGREES = floatPreferencesKey("theme_hue_degrees")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val HAS_PROMPTED_BACKUP_FOLDER = booleanPreferencesKey("has_prompted_backup_folder")
        val LAST_CLEANUP_DATE = longPreferencesKey("last_cleanup_date_millis")
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            weightKg = prefs[Keys.WEIGHT] ?: 75f,
            heightCm = prefs[Keys.HEIGHT] ?: 175f,
            ageYears = prefs[Keys.AGE] ?: 25,
            gender = prefs[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: Gender.MALE,
            activityLevel = prefs[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: ActivityLevel.MODERATE,
            timerVolumePercent = (prefs[Keys.TIMER_VOLUME] ?: 50).coerceIn(0, 100),
            themeHueDegrees = normalizeHueDegrees(prefs[Keys.THEME_HUE_DEGREES] ?: DEFAULT_THEME_HUE_DEGREES)
        )
    }

    /** Persisted SAF tree URI for the user-chosen backup folder (e.g. Documents). Null if not yet configured. */
    val backupFolderUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.BACKUP_FOLDER_URI]
    }

    /** True once the user has been shown the backup-folder picker at least once. */
    val hasPromptedBackupFolder: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAS_PROMPTED_BACKUP_FOLDER] ?: false
    }

    /** Midnight-normalised millis of the last day on which the daily cleanup ran (0 if never). */
    val lastCleanupDateMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_CLEANUP_DATE] ?: 0L
    }

    suspend fun save(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT] = profile.weightKg
            prefs[Keys.HEIGHT] = profile.heightCm
            prefs[Keys.AGE] = profile.ageYears
            prefs[Keys.GENDER] = profile.gender.name
            prefs[Keys.ACTIVITY] = profile.activityLevel.name
            prefs[Keys.TIMER_VOLUME] = profile.timerVolumePercent.coerceIn(0, 100)
            prefs[Keys.THEME_HUE_DEGREES] = normalizeHueDegrees(profile.themeHueDegrees)
        }
    }

    suspend fun saveBackupFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.BACKUP_FOLDER_URI] = uri
            else prefs.remove(Keys.BACKUP_FOLDER_URI)
        }
    }

    suspend fun markBackupFolderPrompted() {
        context.dataStore.edit { prefs -> prefs[Keys.HAS_PROMPTED_BACKUP_FOLDER] = true }
    }

    suspend fun saveLastCleanupDateMillis(dateMillis: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_CLEANUP_DATE] = dateMillis }
    }
}
