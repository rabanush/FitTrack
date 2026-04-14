package com.fittrack.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fittrack.app.data.model.Workout
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
class WorkoutListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeExerciseDao: FakeExerciseDao
    private lateinit var fakeWorkoutDao: FakeWorkoutDao
    private lateinit var fakeLogEntryDao: FakeLogEntryDao
    private lateinit var repository: FitTrackRepository
    private lateinit var viewModel: WorkoutListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeExerciseDao = FakeExerciseDao()
        fakeWorkoutDao = FakeWorkoutDao()
        fakeLogEntryDao = FakeLogEntryDao()
        repository = FitTrackRepository(fakeExerciseDao, fakeWorkoutDao, fakeLogEntryDao)
        viewModel = WorkoutListViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createWorkout_uppercasesName() = runTest {
        viewModel.createWorkout("chest day") {}
        advanceUntilIdle()
        val workouts = repository.allWorkouts.first()
        assertEquals(1, workouts.size)
        assertEquals("CHEST DAY", workouts[0].name)
    }

    @Test
    fun createWorkout_mixedCase_uppercasesName() = runTest {
        viewModel.createWorkout("Push Pull Legs") {}
        advanceUntilIdle()
        val workouts = repository.allWorkouts.first()
        assertEquals("PUSH PULL LEGS", workouts[0].name)
    }

    @Test
    fun createWorkout_invokesCallback() = runTest {
        var callbackId = -1L
        viewModel.createWorkout("Test") { id -> callbackId = id }
        advanceUntilIdle()
        assertTrue(callbackId > 0)
    }

    @Test
    fun deleteWorkout_removesFromRepository() = runTest {
        val id = fakeWorkoutDao.insertWorkout(Workout(name = "Upper Body"))
        val workout = fakeWorkoutDao.getWorkoutById(id)!!
        advanceUntilIdle()

        viewModel.deleteWorkout(workout)
        advanceUntilIdle()

        val workouts = repository.allWorkouts.first()
        assertFalse(workouts.any { it.id == id })
    }

    @Test
    fun deleteWorkout_withMultiple_removesOnlyTarget() = runTest {
        val id1 = fakeWorkoutDao.insertWorkout(Workout(name = "Day A"))
        val id2 = fakeWorkoutDao.insertWorkout(Workout(name = "Day B"))
        val workout1 = fakeWorkoutDao.getWorkoutById(id1)!!

        viewModel.deleteWorkout(workout1)
        advanceUntilIdle()

        val workouts = repository.allWorkouts.first()
        assertEquals(1, workouts.size)
        assertEquals(id2, workouts[0].id)
    }

    @Test
    fun createWorkout_emptyRepository_firstWorkoutHasId() = runTest {
        viewModel.createWorkout("My Workout") {}
        advanceUntilIdle()
        val workouts = repository.allWorkouts.first()
        assertTrue(workouts[0].id > 0)
    }
}
