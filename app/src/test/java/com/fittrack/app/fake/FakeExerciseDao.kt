package com.fittrack.app.fake

import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeExerciseDao : ExerciseDao {
    private val exercises = mutableListOf<Exercise>()
    private val flow = MutableStateFlow<List<Exercise>>(emptyList())
    private var nextId = 1L

    override fun getAllExercises(): Flow<List<Exercise>> = flow

    override suspend fun getExerciseById(id: Long): Exercise? = exercises.find { it.id == id }

    override suspend fun insertExercise(exercise: Exercise): Long {
        val id = if (exercise.id == 0L) nextId++ else exercise.id
        exercises.removeIf { it.id == id }
        exercises.add(exercise.copy(id = id))
        flow.value = exercises.toList()
        return id
    }

    override suspend fun updateExercise(exercise: Exercise) {
        val idx = exercises.indexOfFirst { it.id == exercise.id }
        if (idx >= 0) {
            exercises[idx] = exercise
            flow.value = exercises.toList()
        }
    }

    override suspend fun deleteExercise(exercise: Exercise) {
        exercises.removeIf { it.id == exercise.id }
        flow.value = exercises.toList()
    }

    override suspend fun getCount(): Int = exercises.size
}
