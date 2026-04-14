package com.fittrack.app.data.dao

import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Exercise
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
class ExerciseDaoTest {

    private lateinit var db: FitTrackDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setup() {
        db = TestDatabase.create()
        dao = db.exerciseDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runTest {
        val exercise = Exercise(name = "Bench Press", muscleGroup = "Chest")
        val id = dao.insertExercise(exercise)
        val retrieved = dao.getExerciseById(id)
        assertNotNull(retrieved)
        assertEquals("Bench Press", retrieved!!.name)
        assertEquals("Chest", retrieved.muscleGroup)
    }

    @Test
    fun getAllExercises_returnsAllInserted() = runTest {
        dao.insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        dao.insertExercise(Exercise(name = "Deadlift", muscleGroup = "Back"))
        val exercises = dao.getAllExercises().first()
        assertEquals(2, exercises.size)
    }

    @Test
    fun getAllExercises_orderedByName() = runTest {
        dao.insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        dao.insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
        dao.insertExercise(Exercise(name = "Deadlift", muscleGroup = "Back"))
        val exercises = dao.getAllExercises().first()
        assertEquals("Bench Press", exercises[0].name)
        assertEquals("Deadlift", exercises[1].name)
        assertEquals("Squat", exercises[2].name)
    }

    @Test
    fun updateExercise_modifiesName() = runTest {
        val id = dao.insertExercise(Exercise(name = "Old", muscleGroup = "Chest"))
        val exercise = dao.getExerciseById(id)!!
        dao.updateExercise(exercise.copy(name = "New"))
        val updated = dao.getExerciseById(id)
        assertEquals("New", updated?.name)
    }

    @Test
    fun updateExercise_modifiesMuscleGroup() = runTest {
        val id = dao.insertExercise(Exercise(name = "Press", muscleGroup = "Chest"))
        val exercise = dao.getExerciseById(id)!!
        dao.updateExercise(exercise.copy(muscleGroup = "Shoulders"))
        val updated = dao.getExerciseById(id)
        assertEquals("Shoulders", updated?.muscleGroup)
    }

    @Test
    fun deleteExercise_removesFromDb() = runTest {
        val id = dao.insertExercise(Exercise(name = "Curl", muscleGroup = "Biceps"))
        val exercise = dao.getExerciseById(id)!!
        dao.deleteExercise(exercise)
        assertNull(dao.getExerciseById(id))
    }

    @Test
    fun deleteExercise_doesNotAffectOthers() = runTest {
        val id1 = dao.insertExercise(Exercise(name = "A", muscleGroup = "Chest"))
        val id2 = dao.insertExercise(Exercise(name = "B", muscleGroup = "Back"))
        val ex1 = dao.getExerciseById(id1)!!
        dao.deleteExercise(ex1)
        val exercises = dao.getAllExercises().first()
        assertEquals(1, exercises.size)
        assertEquals(id2, exercises[0].id)
    }

    @Test
    fun getCount_returnsCorrectNumber() = runTest {
        assertEquals(0, dao.getCount())
        dao.insertExercise(Exercise(name = "A", muscleGroup = "X"))
        assertEquals(1, dao.getCount())
        dao.insertExercise(Exercise(name = "B", muscleGroup = "Y"))
        assertEquals(2, dao.getCount())
    }

    @Test
    fun getById_nonExistent_returnsNull() = runTest {
        val result = dao.getExerciseById(999L)
        assertNull(result)
    }

    @Test
    fun insert_autoGeneratesId() = runTest {
        val id = dao.insertExercise(Exercise(name = "Test", muscleGroup = "Back"))
        assertTrue(id > 0)
    }

    @Test
    fun getAllExercises_emptyDb_returnsEmpty() = runTest {
        val exercises = dao.getAllExercises().first()
        assertTrue(exercises.isEmpty())
    }

    @Test
    fun insertWithReplace_updatesExistingId() = runTest {
        val id = dao.insertExercise(Exercise(name = "Original", muscleGroup = "Chest"))
        dao.insertExercise(Exercise(id = id, name = "Replaced", muscleGroup = "Chest"))
        val retrieved = dao.getExerciseById(id)
        assertEquals("Replaced", retrieved?.name)
        assertEquals(1, dao.getCount())
    }
}
