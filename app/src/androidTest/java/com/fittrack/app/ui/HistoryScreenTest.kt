package com.fittrack.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.data.model.Workout
import com.fittrack.app.ui.screens.HistoryScreen
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.TestDatabase
import com.fittrack.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: FitTrackDatabase
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        db = TestDatabase.create()
        val repository = TestDatabase.createRepository(db)
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun launchScreen(onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            FitTrackTheme {
                HistoryScreen(viewModel = viewModel, onBack = onBack)
            }
        }
    }

    @Test
    fun topBar_showsWorkoutHistoryTitle() {
        launchScreen()
        composeTestRule.onNodeWithText("Workout History").assertIsDisplayed()
    }

    @Test
    fun backButton_click_invokesCallback() {
        var backClicked = false
        launchScreen(onBack = { backClicked = true })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun emptyState_showsNoHistoryText() {
        launchScreen()
        composeTestRule.onNodeWithText("No workout history yet.").assertIsDisplayed()
    }

    @Test
    fun withLogEntries_showsDateCard() = runTest {
        val workoutId = db.workoutDao().insertWorkout(Workout(name = "Test"))
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
        val date = 1_700_000_000_000L
        db.logEntryDao().insertLogEntry(
            LogEntry(exerciseId = exerciseId, workoutId = workoutId, date = date, setNumber = 1, weight = 80f, reps = 10, rir = 2)
        )
        launchScreen()
        val expectedDate = SimpleDateFormat("EEE, MMM d yyyy HH:mm", Locale.getDefault()).format(Date(date))
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText(expectedDate).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
    }

    @Test
    fun dateCard_showsHideAndShowButton() = runTest {
        val workoutId = db.workoutDao().insertWorkout(Workout(name = "Test"))
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Bench", muscleGroup = "Chest"))
        db.logEntryDao().insertLogEntry(
            LogEntry(exerciseId = exerciseId, workoutId = workoutId, date = 1_700_000_000_000L, setNumber = 1, weight = 60f, reps = 8, rir = 1)
        )
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Show").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun dateCard_showClick_revealsEntries() = runTest {
        val workoutId = db.workoutDao().insertWorkout(Workout(name = "Test"))
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Deadlift", muscleGroup = "Back"))
        db.logEntryDao().insertLogEntry(
            LogEntry(exerciseId = exerciseId, workoutId = workoutId, date = 1_700_000_000_000L, setNumber = 1, weight = 100f, reps = 5, rir = 0)
        )
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Show").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Show").performClick()
        composeTestRule.onNodeWithText("Set 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("100.0kg × 5 reps, RIR: 0").assertIsDisplayed()
    }

    @Test
    fun dateCard_showThenHide_hidesEntries() = runTest {
        val workoutId = db.workoutDao().insertWorkout(Workout(name = "Test"))
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        db.logEntryDao().insertLogEntry(
            LogEntry(exerciseId = exerciseId, workoutId = workoutId, date = 1_700_000_000_000L, setNumber = 1, weight = 120f, reps = 6, rir = 1)
        )
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Show").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Show").performClick()
        composeTestRule.onNodeWithText("Set 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hide").performClick()
        composeTestRule.onNodeWithText("Set 1").assertDoesNotExist()
    }

    @Test
    fun multipleDates_showsAllDateCards() = runTest {
        val workoutId = db.workoutDao().insertWorkout(Workout(name = "Test"))
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Press", muscleGroup = "Chest"))
        val date1 = 1_700_000_000_000L
        val date2 = 1_700_100_000_000L
        db.logEntryDao().insertLogEntry(
            LogEntry(exerciseId = exerciseId, workoutId = workoutId, date = date1, setNumber = 1, weight = 60f, reps = 10, rir = 2)
        )
        db.logEntryDao().insertLogEntry(
            LogEntry(exerciseId = exerciseId, workoutId = workoutId, date = date2, setNumber = 1, weight = 65f, reps = 10, rir = 2)
        )
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Show").fetchSemanticsNodes().size >= 2
        }
        val showButtons = composeTestRule.onAllNodesWithText("Show").fetchSemanticsNodes()
        assert(showButtons.size == 2)
    }
}
