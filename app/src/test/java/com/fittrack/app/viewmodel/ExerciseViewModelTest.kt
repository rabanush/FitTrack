package com.fittrack.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.fake.FakeExerciseDao
import com.fittrack.app.fake.FakeLogEntryDao
import com.fittrack.app.fake.FakeWorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeExerciseDao: FakeExerciseDao
    private lateinit var repository: FitTrackRepository
    private lateinit var viewModel: ExerciseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeExerciseDao = FakeExerciseDao()
        repository = FitTrackRepository(fakeExerciseDao, FakeWorkoutDao(), FakeLogEntryDao())
        viewModel = ExerciseViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun insertExercise_addsToList() = runTest {
        viewModel.insertExercise("Bench Press", "Chest")
        advanceUntilIdle()
        val exercises = viewModel.allExercises.first { it.isNotEmpty() }
        assertEquals(1, exercises.size)
        assertEquals("Bench Press", exercises[0].name)
        assertEquals("Chest", exercises[0].muscleGroup)
        assertTrue(exercises[0].isCustom)
    }

    @Test
    fun insertExercise_multipleExercises_allPresent() = runTest {
        viewModel.insertExercise("Squat", "Quads")
        viewModel.insertExercise("Deadlift", "Back")
        advanceUntilIdle()
        val exercises = viewModel.allExercises.first { it.size >= 2 }
        assertEquals(2, exercises.size)
    }

    @Test
    fun updateExercise_modifiesName() = runTest {
        val id = fakeExerciseDao.insertExercise(
            Exercise(name = "Old Name", muscleGroup = "Chest", isCustom = true)
        )
        val exercise = fakeExerciseDao.getExerciseById(id)!!
        viewModel.updateExercise(exercise.copy(name = "New Name"))
        advanceUntilIdle()
        val updated = fakeExerciseDao.getExerciseById(id)
        assertEquals("New Name", updated?.name)
    }

    @Test
    fun updateExercise_modifiesMuscleGroup() = runTest {
        val id = fakeExerciseDao.insertExercise(
            Exercise(name = "Press", muscleGroup = "Chest", isCustom = true)
        )
        val exercise = fakeExerciseDao.getExerciseById(id)!!
        viewModel.updateExercise(exercise.copy(muscleGroup = "Shoulders"))
        advanceUntilIdle()
        val updated = fakeExerciseDao.getExerciseById(id)
        assertEquals("Shoulders", updated?.muscleGroup)
    }

    @Test
    fun deleteExercise_removesFromList() = runTest {
        val id = fakeExerciseDao.insertExercise(
            Exercise(name = "Curl", muscleGroup = "Biceps", isCustom = true)
        )
        val exercise = fakeExerciseDao.getExerciseById(id)!!
        viewModel.deleteExercise(exercise)
        advanceUntilIdle()
        val exercises = repository.allExercises.first()
        assertFalse(exercises.any { it.id == id })
    }

    @Test
    fun deleteExercise_withMultiple_removesOnlyTarget() = runTest {
        val id1 = fakeExerciseDao.insertExercise(Exercise(name = "A", muscleGroup = "X", isCustom = true))
        val id2 = fakeExerciseDao.insertExercise(Exercise(name = "B", muscleGroup = "Y", isCustom = true))
        val exercise1 = fakeExerciseDao.getExerciseById(id1)!!

        viewModel.deleteExercise(exercise1)
        advanceUntilIdle()

        val exercises = repository.allExercises.first()
        assertEquals(1, exercises.size)
        assertEquals(id2, exercises[0].id)
    }

    @Test
    fun insertExercise_defaultIsCustomTrue() = runTest {
        viewModel.insertExercise("Test", "Back")
        advanceUntilIdle()
        val exercises = viewModel.allExercises.first { it.isNotEmpty() }
        assertTrue(exercises[0].isCustom)
    }
}
