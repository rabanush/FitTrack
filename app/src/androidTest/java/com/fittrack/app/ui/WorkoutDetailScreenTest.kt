package com.fittrack.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.ui.screens.WorkoutDetailScreen
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.TestDatabase
import com.fittrack.app.viewmodel.WorkoutDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WorkoutDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: FitTrackDatabase
    private var workoutId = 0L

    @Before
    fun setup() = runTest {
        db = TestDatabase.create()
        workoutId = db.workoutDao().insertWorkout(Workout(name = "PUSH DAY"))
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun createViewModel(): WorkoutDetailViewModel {
        val repository = TestDatabase.createRepository(db)
        return WorkoutDetailViewModel(repository, workoutId)
    }

    private fun launchScreen(
        viewModel: WorkoutDetailViewModel = createViewModel(),
        onBack: () -> Unit = {},
        onStartWorkout: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FitTrackTheme {
                WorkoutDetailScreen(
                    viewModel = viewModel,
                    onBack = onBack,
                    onStartWorkout = onStartWorkout
                )
            }
        }
    }

    @Test
    fun topBar_showsWorkoutName() {
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("PUSH DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PUSH DAY").assertIsDisplayed()
    }

    @Test
    fun backButton_click_invokesCallback() {
        var backClicked = false
        launchScreen(onBack = { backClicked = true })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun startWorkoutButton_click_invokesCallback() {
        var startClicked = false
        launchScreen(onStartWorkout = { startClicked = true })
        composeTestRule.onNodeWithContentDescription("Start Workout").performClick()
        assert(startClicked)
    }

    @Test
    fun emptyExercises_showsAddPrompt() {
        launchScreen()
        composeTestRule.onNodeWithText("No exercises added yet. Tap + to add exercises.").assertIsDisplayed()
    }

    @Test
    fun fab_click_opensAddExerciseDialog() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.onNodeWithText("Add Exercise").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test
    fun addExerciseDialog_closeButton_dismissesDialog() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.onNodeWithText("Add Exercise").assertDoesNotExist()
    }

    @Test
    fun addExerciseDialog_listShowsAvailableExercises() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Bench Press").assertIsDisplayed()
    }

    @Test
    fun addExerciseDialog_search_filtersExercises() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
        db.exerciseDao().insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Search").performTextInput("Bench")
        composeTestRule.onNodeWithText("Bench Press").assertIsDisplayed()
        composeTestRule.onNodeWithText("Squat").assertDoesNotExist()
    }

    @Test
    fun addExerciseToWorkout_appearsInList() = runTest {
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Overhead Press", muscleGroup = "Shoulders"))
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Overhead Press").fetchSemanticsNodes().isNotEmpty()
        }
        // Click the Add icon next to the exercise
        composeTestRule.onNodeWithContentDescription("Add").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Overhead Press").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("Add Exercise").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Overhead Press").assertIsDisplayed()
    }

    @Test
    fun exerciseItem_showsRestTimerInfo() = runTest {
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Pull-ups", muscleGroup = "Back"))
        db.workoutDao().insertWorkoutExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, restTimerSeconds = 90)
        )
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Pull-ups").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("90s rest").assertIsDisplayed()
    }

    @Test
    fun exerciseItem_deleteButton_removesFromWorkout() = runTest {
        val exerciseId = db.exerciseDao().insertExercise(Exercise(name = "Dips", muscleGroup = "Chest"))
        db.workoutDao().insertWorkoutExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId)
        )
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Dips").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Remove").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Dips").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Dips").assertDoesNotExist()
    }

    @Test
    fun editName_click_togglesToEditMode() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Edit Name").performClick()
        composeTestRule.onNodeWithContentDescription("Save").assertIsDisplayed()
    }

    @Test
    fun editName_saveUpdatesWorkoutName() = runTest {
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("PUSH DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Edit Name").performClick()
        val textField = composeTestRule.onNodeWithText("PUSH DAY")
        textField.performTextInput(" + CHEST")
        composeTestRule.onNodeWithContentDescription("Save").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("PUSH DAY + CHEST").fetchSemanticsNodes().isNotEmpty()
        }
        val updatedWorkout = db.workoutDao().getWorkoutById(workoutId)
        assertEquals("PUSH DAY + CHEST", updatedWorkout?.name)
    }
}
