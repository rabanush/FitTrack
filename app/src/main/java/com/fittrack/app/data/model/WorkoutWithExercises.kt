package com.fittrack.app.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class WorkoutWithExercises(
    @Embedded val workout: Workout,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = WorkoutExercise::class,
            parentColumn = "workout_id",
            entityColumn = "exercise_id"
        )
    )
    val exercises: List<Exercise>
)
