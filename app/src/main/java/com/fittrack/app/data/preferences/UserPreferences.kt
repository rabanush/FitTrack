package com.fittrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

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
    val timerVolumePercent: Int = 50
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
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val HAS_PROMPTED_BACKUP_FOLDER = booleanPreferencesKey("has_prompted_backup_folder")
        val RECENT_FOOD_USAGES = stringSetPreferencesKey("recent_food_usages")
    }

    data class RecentFoodUsage(
        val name: String,
        val barcode: String?,
        val addedAtMillis: Long
    )

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            weightKg = prefs[Keys.WEIGHT] ?: 75f,
            heightCm = prefs[Keys.HEIGHT] ?: 175f,
            ageYears = prefs[Keys.AGE] ?: 25,
            gender = prefs[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: Gender.MALE,
            activityLevel = prefs[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: ActivityLevel.MODERATE,
            timerVolumePercent = (prefs[Keys.TIMER_VOLUME] ?: 50).coerceIn(0, 100)
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

    suspend fun saveBackupFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.BACKUP_FOLDER_URI] = uri
            else prefs.remove(Keys.BACKUP_FOLDER_URI)
        }
    }

    suspend fun markBackupFolderPrompted() {
        context.dataStore.edit { prefs -> prefs[Keys.HAS_PROMPTED_BACKUP_FOLDER] = true }
    }

    suspend fun addRecentFoodUsage(
        name: String,
        barcode: String?,
        addedAtMillis: Long,
        retentionDays: Long
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val cutoffMillis = addedAtMillis - (retentionDays * MILLIS_PER_DAY)
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.RECENT_FOOD_USAGES].orEmpty()
            val prunedAndCapped = existing.asSequence()
                .mapNotNull(::decodeRecentFoodUsage)
                .plus(
                    RecentFoodUsage(
                        name = trimmedName,
                        barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
                        addedAtMillis = addedAtMillis
                    )
                )
                .filter { it.addedAtMillis >= cutoffMillis }
                .sortedByDescending { it.addedAtMillis }
                .take(MAX_RECENT_FOOD_USAGE_ITEMS)
            prefs[Keys.RECENT_FOOD_USAGES] = prunedAndCapped.map(::encodeRecentFoodUsage).toSet()
        }
    }

    suspend fun getRecentFoodUsagesSince(sinceMillis: Long): List<RecentFoodUsage> {
        val encoded = context.dataStore.data.map { it[Keys.RECENT_FOOD_USAGES].orEmpty() }.first()
        return encoded.asSequence()
            .mapNotNull(::decodeRecentFoodUsage)
            .filter { it.addedAtMillis >= sinceMillis && it.name.isNotBlank() }
            .sortedByDescending { it.addedAtMillis }
            .toList()
    }

    private fun encodeRecentFoodUsage(usage: RecentFoodUsage): String {
        return JSONObject()
            .put("addedAtMillis", usage.addedAtMillis)
            .put("name", usage.name)
            .put("barcode", usage.barcode)
            .toString()
    }

    private fun decodeRecentFoodUsage(encoded: String): RecentFoodUsage? {
        val obj = runCatching { JSONObject(encoded) }.getOrNull() ?: return null
        val addedAt = obj.optLong("addedAtMillis", Long.MIN_VALUE)
        if (addedAt == Long.MIN_VALUE) return null
        val name = obj.optString("name", "")
        if (name.isEmpty()) return null
        val barcode = obj.optString("barcode", "").trim().takeIf { it.isNotEmpty() }
        return RecentFoodUsage(name = name, barcode = barcode, addedAtMillis = addedAt)
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        const val MAX_RECENT_FOOD_USAGE_ITEMS = 2000
    }
}
