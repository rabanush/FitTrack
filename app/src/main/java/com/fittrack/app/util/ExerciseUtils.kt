package com.fittrack.app.util

import com.fittrack.app.data.model.Exercise

/** Returns true if the exercise matches [query] against name, muscle group, or German name. */
fun Exercise.matchesQuery(query: String): Boolean =
    query.isEmpty() ||
        name.contains(query, ignoreCase = true) ||
        muscleGroup.contains(query, ignoreCase = true) ||
        germanName.contains(query, ignoreCase = true)

/** Prefers German exercise names in the UI while keeping English as fallback. */
fun Exercise.displayName(): String = germanName.ifBlank { name }

/** Returns the English name as secondary label when a German primary name exists. */
fun Exercise.englishSecondaryName(): String? =
    name.takeIf { germanName.isNotBlank() && !germanName.equals(name, ignoreCase = true) }
