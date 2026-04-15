package com.fittrack.app.util

import com.fittrack.app.data.model.Exercise

/** Returns true if the exercise matches [query] against name, muscle group, or German name. */
fun Exercise.matchesQuery(query: String): Boolean =
    query.isEmpty() ||
        name.contains(query, ignoreCase = true) ||
        muscleGroup.contains(query, ignoreCase = true) ||
        germanName.contains(query, ignoreCase = true)
