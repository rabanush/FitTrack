package com.fittrack.app.data.dao

import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.util.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var db: FitTrackDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseDao: ExerciseDao

    @Before
    fun setup() {
        db = TestDatabase.create()
        workoutDao = db.workoutDao()
        exerciseDao = db.exerciseDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runTest {
        val id = workoutDao.insertWorkout(Workout(name = "Push Day"))
        val retrieved = workoutDao.getWorkoutById(id)
        assertNotNull(retrieved)
        assertEquals("Push Day", retrieved!!.name)
    }

    @Test
    fun getAllWorkouts_returnsAllInserted() = runTest {
        workoutDao.insertWorkout(Workout(name = "Push Day"))
        workoutDao.insertWorkout(Workout(name = "Pull Day"))
        val workouts = workoutDao.getAllWorkouts().first()
        assertEquals(2, workouts.size)
    }

    @Test
    fun getAllWorkouts_orderedByName() = runTest {
        workoutDao.insertWorkout(Workout(name = "Legs"))
        workoutDao.insertWorkout(Workout(name = "Arms"))
        workoutDao.insertWorkout(Workout(name = "Back"))
        val workouts = workoutDao.getAllWorkouts().first()
        assertEquals("Arms", workouts[0].name)
        assertEquals("Back", workouts[1].name)
        assertEquals("Legs", workouts[2].name)
    }

    @Test
    fun updateWorkout_modifiesName() = runTest {
        val id = workoutDao.insertWorkout(Workout(name = "Old"))
        val workout = workoutDao.getWorkoutById(id)!!
        workoutDao.updateWorkout(workout.copy(name = "New"))
        val updated = workoutDao.getWorkoutById(id)
        assertEquals("New", updated?.name)
    }

    @Test
    fun deleteWorkout_removesFromDb() = runTest {
        val id = workoutDao.insertWorkout(Workout(name = "Temp"))
        val workout = workoutDao.getWorkoutById(id)!!
        workoutDao.deleteWorkout(workout)
        assertNull(workoutDao.getWorkoutById(id))
    }

    @Test
    fun getById_nonExistent_returnsNull() = runTest {
        assertNull(workoutDao.getWorkoutById(999L))
    }

    @Test
    fun getAllWorkouts_emptyDb_returnsEmpty() = runTest {
        val workouts = workoutDao.getAllWorkouts().first()
        assertTrue(workouts.isEmpty())
    }

    @Test
    fun insertWorkoutExercise_andRetrieveWithExercise() = runTest {
        val workoutId = workoutDao.insertWorkout(Workout(name = "Full Body"))
        val exerciseId = exerciseDao.insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        workoutDao.insertWorkoutExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId)
        )
        val items = workoutDao.getWorkoutExercisesWithExercise(workoutId).first()
        assertEquals(1, items.size)
        assertEquals(exerciseId, items[0].exercise.id)
        assertEquals("Squat", items[0].exercise.name)
    }

    @Test
    fun getWorkoutExercises_orderedByOrderIndex() = runTest {
        val workoutId = workoutDao.insertWorkout(Workout(name = "Test"))
        val ex1 = exerciseDao.insertExercise(Exercise(name = "B", muscleGroup = "X"))
        val ex2 = exerciseDao.insertExercise(Exercise(name = "A", muscleGroup = "Y"))
        workoutDao.insertWorkoutExercise(WorkoutExercise(workoutId = workoutId, exerciseId = ex1, orderIndex = 1))
        workoutDao.insertWorkoutExercise(WorkoutExercise(workoutId = workoutId, exerciseId = ex2, orderIndex = 0))
        val items = workoutDao.getWorkoutExercisesWithExercise(workoutId).first()
        assertEquals(ex2, items[0].exercise.id) // orderIndex 0 comes first
        assertEquals(ex1, items[1].exercise.id) // orderIndex 1 comes second
    }

    @Test
    fun deleteWorkoutExercise_removesEntry() = runTest {
        val workoutId = workoutDao.insertWorkout(Workout(name = "Test"))
        val exerciseId = exerciseDao.insertExercise(Exercise(name = "Curl", muscleGroup = "Biceps"))
        val weId = workoutDao.insertWorkoutExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId)
        )
        val we = WorkoutExercise(id = weId, workoutId = workoutId, exerciseId = exerciseId)
        workoutDao.deleteWorkoutExercise(we)
        val items = workoutDao.getWorkoutExercisesWithExercise(workoutId).first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun deleteAllWorkoutExercises_removesAllForWorkout() = runTest {
        val workoutId = workoutDao.insertWorkout(Workout(name = "Test"))
        val ex1 = exerciseDao.insertExercise(Exercise(name = "A", muscleGroup = "X"))
        val ex2 = exerciseDao.insertExercise(Exercise(name = "B", muscleGroup = "Y"))
        workoutDao.insertWorkoutExercise(WorkoutExercise(workoutId = workoutId, exerciseId = ex1))
        workoutDao.insertWorkoutExercise(WorkoutExercise(workoutId = workoutId, exerciseId = ex2))
        workoutDao.deleteAllWorkoutExercises(workoutId)
        val items = workoutDao.getWorkoutExercisesWithExercise(workoutId).first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun updateWorkoutExercise_changesRestTimer() = runTest {
        val workoutId = workoutDao.insertWorkout(Workout(name = "Test"))
        val exerciseId = exerciseDao.insertExercise(Exercise(name = "Press", muscleGroup = "Chest"))
        val weId = workoutDao.insertWorkoutExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, restTimerSeconds = 90)
        )
        val we = WorkoutExercise(id = weId, workoutId = workoutId, exerciseId = exerciseId, restTimerSeconds = 90)
        workoutDao.updateWorkoutExercise(we.copy(restTimerSeconds = 120))
        val items = workoutDao.getWorkoutExercises(workoutId).first()
        assertEquals(120, items[0].restTimerSeconds)
    }

    @Test
    fun deleteWorkout_cascadesWorkoutExercises() = runTest {
        val workoutId = workoutDao.insertWorkout(Workout(name = "Test"))
        val exerciseId = exerciseDao.insertExercise(Exercise(name = "A", muscleGroup = "X"))
        workoutDao.insertWorkoutExercise(WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId))

        val workout = workoutDao.getWorkoutById(workoutId)!!
        workoutDao.deleteWorkout(workout)

        val items = workoutDao.getWorkoutExercisesWithExercise(workoutId).first()
        assertTrue(items.isEmpty())
    }
}
