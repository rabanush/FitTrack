package com.fittrack.app.data.dao

import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.data.model.Workout
import com.fittrack.app.util.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LogEntryDaoTest {

    private lateinit var db: FitTrackDatabase
    private lateinit var logEntryDao: LogEntryDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseDao: ExerciseDao

    private var workoutId = 0L
    private var exerciseId = 0L

    @Before
    fun setup() = runTest {
        db = TestDatabase.create()
        logEntryDao = db.logEntryDao()
        workoutDao = db.workoutDao()
        exerciseDao = db.exerciseDao()
        workoutId = workoutDao.insertWorkout(Workout(name = "Test Workout"))
        exerciseId = exerciseDao.insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun makeEntry(
        date: Long = System.currentTimeMillis(),
        setNumber: Int = 1,
        weight: Float = 80f,
        reps: Int = 10,
        rir: Int = 2,
        exId: Long = exerciseId,
        wId: Long = workoutId
    ) = LogEntry(
        exerciseId = exId,
        workoutId = wId,
        date = date,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        rir = rir
    )

    @Test
    fun insertAndGetAll() = runTest {
        logEntryDao.insertLogEntry(makeEntry(setNumber = 1))
        logEntryDao.insertLogEntry(makeEntry(setNumber = 2))
        val entries = logEntryDao.getAllLogEntries().first()
        assertEquals(2, entries.size)
    }

    @Test
    fun insertMultiple_insertLogEntries() = runTest {
        val entries = listOf(
            makeEntry(setNumber = 1),
            makeEntry(setNumber = 2),
            makeEntry(setNumber = 3)
        )
        logEntryDao.insertLogEntries(entries)
        val all = logEntryDao.getAllLogEntries().first()
        assertEquals(3, all.size)
    }

    @Test
    fun getAllLogEntries_orderedByDateDesc() = runTest {
        val now = System.currentTimeMillis()
        logEntryDao.insertLogEntry(makeEntry(date = now - 1000))
        logEntryDao.insertLogEntry(makeEntry(date = now))
        val entries = logEntryDao.getAllLogEntries().first()
        assertTrue(entries[0].date >= entries[1].date)
    }

    @Test
    fun deleteLogEntry_removesEntry() = runTest {
        val id = logEntryDao.insertLogEntry(makeEntry())
        val entry = logEntryDao.getAllLogEntries().first().first { it.id == id }
        logEntryDao.deleteLogEntry(entry)
        val all = logEntryDao.getAllLogEntries().first()
        assertTrue(all.none { it.id == id })
    }

    @Test
    fun getAllWorkoutDates_returnsDistinctDates() = runTest {
        val date1 = 1_000_000L
        val date2 = 2_000_000L
        logEntryDao.insertLogEntry(makeEntry(date = date1, setNumber = 1))
        logEntryDao.insertLogEntry(makeEntry(date = date1, setNumber = 2))
        logEntryDao.insertLogEntry(makeEntry(date = date2, setNumber = 1))
        val dates = logEntryDao.getAllWorkoutDates().first()
        assertEquals(2, dates.size)
        assertTrue(dates.contains(date1))
        assertTrue(dates.contains(date2))
    }

    @Test
    fun getAllWorkoutDates_orderedDesc() = runTest {
        val date1 = 1_000_000L
        val date2 = 3_000_000L
        val date3 = 2_000_000L
        logEntryDao.insertLogEntry(makeEntry(date = date1))
        logEntryDao.insertLogEntry(makeEntry(date = date2))
        logEntryDao.insertLogEntry(makeEntry(date = date3))
        val dates = logEntryDao.getAllWorkoutDates().first()
        assertEquals(date2, dates[0])
        assertEquals(date3, dates[1])
        assertEquals(date1, dates[2])
    }

    @Test
    fun getLogEntriesForDate_returnsOnlyThatDate() = runTest {
        val targetDate = 5_000_000L
        val otherDate = 6_000_000L
        logEntryDao.insertLogEntry(makeEntry(date = targetDate, setNumber = 1))
        logEntryDao.insertLogEntry(makeEntry(date = targetDate, setNumber = 2))
        logEntryDao.insertLogEntry(makeEntry(date = otherDate, setNumber = 1))
        val entries = logEntryDao.getLogEntriesForDate(targetDate).first()
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.date == targetDate })
    }

    @Test
    fun getLogEntriesForExercise_returnsOnlyThatExercise() = runTest {
        val ex2Id = exerciseDao.insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        logEntryDao.insertLogEntry(makeEntry(exId = exerciseId, setNumber = 1))
        logEntryDao.insertLogEntry(makeEntry(exId = ex2Id, setNumber = 1))
        val entries = logEntryDao.getLogEntriesForExercise(exerciseId).first()
        assertEquals(1, entries.size)
        assertEquals(exerciseId, entries[0].exerciseId)
    }

    @Test
    fun getPreviousLogEntriesForExercise_returnsLatestBeforeDate() = runTest {
        val now = System.currentTimeMillis()
        val pastDate = now - 10_000
        logEntryDao.insertLogEntry(makeEntry(date = pastDate, setNumber = 1, weight = 70f))
        logEntryDao.insertLogEntry(makeEntry(date = pastDate, setNumber = 2, weight = 70f))
        val entries = logEntryDao.getPreviousLogEntriesForExercise(exerciseId, now)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.date == pastDate })
    }

    @Test
    fun getPreviousLogEntriesForExercise_ignoresDateAtOrAfterBeforeDate() = runTest {
        val now = System.currentTimeMillis()
        logEntryDao.insertLogEntry(makeEntry(date = now, setNumber = 1))
        val entries = logEntryDao.getPreviousLogEntriesForExercise(exerciseId, now)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun getPreviousLogEntriesForExercise_returnsOnlyMostRecentPastSession() = runTest {
        val now = System.currentTimeMillis()
        val pastDate1 = now - 20_000
        val pastDate2 = now - 10_000
        logEntryDao.insertLogEntry(makeEntry(date = pastDate1, setNumber = 1, weight = 60f))
        logEntryDao.insertLogEntry(makeEntry(date = pastDate2, setNumber = 1, weight = 80f))
        val entries = logEntryDao.getPreviousLogEntriesForExercise(exerciseId, now)
        assertEquals(1, entries.size)
        assertEquals(80f, entries[0].weight)
    }

    @Test
    fun getPreviousLogEntries_withWorkoutId_returnsMatchingEntries() = runTest {
        val now = System.currentTimeMillis()
        val pastDate = now - 10_000
        logEntryDao.insertLogEntry(makeEntry(date = pastDate, setNumber = 1, weight = 75f))
        val entries = logEntryDao.getPreviousLogEntries(exerciseId, workoutId, now)
        assertEquals(1, entries.size)
        assertEquals(75f, entries[0].weight)
    }

    @Test
    fun deleteLogEntriesForSession_removesMatchingEntries() = runTest {
        val date = 7_000_000L
        logEntryDao.insertLogEntry(makeEntry(date = date, setNumber = 1))
        logEntryDao.insertLogEntry(makeEntry(date = date, setNumber = 2))
        logEntryDao.deleteLogEntriesForSession(exerciseId, workoutId, date)
        val entries = logEntryDao.getLogEntriesForDate(date).first()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun deleteExercise_cascadesLogEntries() = runTest {
        logEntryDao.insertLogEntry(makeEntry(setNumber = 1))
        val exercise = exerciseDao.getExerciseById(exerciseId)!!
        exerciseDao.deleteExercise(exercise)
        val entries = logEntryDao.getAllLogEntries().first()
        assertTrue(entries.none { it.exerciseId == exerciseId })
    }
}
