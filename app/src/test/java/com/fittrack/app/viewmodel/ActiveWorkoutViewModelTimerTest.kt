package com.fittrack.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.fake.FakeExerciseDao
import com.fittrack.app.fake.FakeLogEntryDao
import com.fittrack.app.fake.FakeWorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ActiveWorkoutViewModelTimerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeWorkoutDao: FakeWorkoutDao
    private lateinit var repository: FitTrackRepository
    private lateinit var viewModel: ActiveWorkoutViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeWorkoutDao = FakeWorkoutDao()
        repository = FitTrackRepository(FakeExerciseDao(), fakeWorkoutDao, FakeLogEntryDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun createViewModelForWorkout(): ActiveWorkoutViewModel {
        val workoutId = fakeWorkoutDao.insertWorkout(Workout(name = "Test Workout"))
        return ActiveWorkoutViewModel(repository, workoutId)
    }

    @Test
    fun startTimer_setsIsRunningTrue() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(60, 0, 1)
        advanceUntilIdle()
        val state = vm.timerState.value
        assertTrue(state.isRunning)
        assertEquals(0, state.exerciseIndex)
        assertEquals(1, state.setNumber)
    }

    @Test
    fun startTimer_setsCorrectTotalAndRemainingSeconds() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(90, 0, 1)
        advanceUntilIdle()
        val state = vm.timerState.value
        assertEquals(90, state.totalSeconds)
        assertTrue(state.remainingSeconds in 88..90) // allow slight clock drift
    }

    @Test
    fun skipTimer_stopsTimer() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(60, 0, 1)
        advanceUntilIdle()
        assertTrue(vm.timerState.value.isRunning)

        vm.skipTimer()
        assertFalse(vm.timerState.value.isRunning)
        assertEquals(0, vm.timerState.value.remainingSeconds)
    }

    @Test
    fun skipTimer_whenNoTimer_remainsNonRunning() = runTest {
        val vm = createViewModelForWorkout()
        vm.skipTimer()
        assertFalse(vm.timerState.value.isRunning)
        assertEquals(0, vm.timerState.value.remainingSeconds)
    }

    @Test
    fun adjustTimer_increasesRemainingTime() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(60, 0, 1)
        advanceUntilIdle()
        val beforeRemaining = vm.timerState.value.remainingSeconds

        vm.adjustTimer(30)
        val afterRemaining = vm.timerState.value.remainingSeconds
        assertTrue(afterRemaining > beforeRemaining)
    }

    @Test
    fun adjustTimer_decreasesRemainingTime() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(120, 0, 1)
        advanceUntilIdle()
        val beforeRemaining = vm.timerState.value.remainingSeconds

        vm.adjustTimer(-30)
        val afterRemaining = vm.timerState.value.remainingSeconds
        assertTrue(afterRemaining < beforeRemaining)
    }

    @Test
    fun adjustTimer_whenNotRunning_doesNothing() = runTest {
        val vm = createViewModelForWorkout()
        vm.adjustTimer(30)
        assertEquals(0, vm.timerState.value.remainingSeconds)
        assertFalse(vm.timerState.value.isRunning)
    }

    @Test
    fun adjustTimer_bringsBelowZero_callsSkip() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(10, 0, 1)
        advanceUntilIdle()
        assertTrue(vm.timerState.value.isRunning)

        vm.adjustTimer(-100)
        assertFalse(vm.timerState.value.isRunning)
        assertEquals(0, vm.timerState.value.remainingSeconds)
    }

    @Test
    fun isTimerVisible_falseWhenTimerNotStarted() = runTest {
        val vm = createViewModelForWorkout()
        advanceUntilIdle()
        assertFalse(vm.isTimerVisible.value)
    }

    @Test
    fun isTimerVisible_trueAfterStartTimer() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(60, 0, 1)
        advanceUntilIdle()
        assertTrue(vm.isTimerVisible.value)
    }

    @Test
    fun isTimerVisible_falseAfterSkip() = runTest {
        val vm = createViewModelForWorkout()
        vm.startTimer(60, 0, 1)
        advanceUntilIdle()
        vm.skipTimer()
        assertFalse(vm.isTimerVisible.value)
    }
}
