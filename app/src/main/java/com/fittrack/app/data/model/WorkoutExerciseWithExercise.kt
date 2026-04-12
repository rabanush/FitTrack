package com.fittrack.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExercise,
    @Relation(
        parentColumn = "exercise_id",
        entityColumn = "id"
    )
    val exercise: Exercise
)
