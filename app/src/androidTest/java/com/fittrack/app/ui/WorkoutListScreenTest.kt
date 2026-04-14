package com.fittrack.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Workout
import com.fittrack.app.ui.screens.WorkoutListScreen
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.TestDatabase
import com.fittrack.app.viewmodel.WorkoutListViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WorkoutListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: FitTrackDatabase
    private lateinit var viewModel: WorkoutListViewModel

    @Before
    fun setup() {
        db = TestDatabase.create()
        val repository = TestDatabase.createRepository(db)
        viewModel = WorkoutListViewModel(repository)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun launchScreen(
        onWorkoutClick: (Long) -> Unit = {},
        onStartWorkout: (Long) -> Unit = {},
        onExercisesClick: () -> Unit = {},
        onHistoryClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FitTrackTheme {
                WorkoutListScreen(
                    viewModel = viewModel,
                    onWorkoutClick = onWorkoutClick,
                    onStartWorkout = onStartWorkout,
                    onExercisesClick = onExercisesClick,
                    onHistoryClick = onHistoryClick
                )
            }
        }
    }

    @Test
    fun emptyState_showsNoWorkoutsText() {
        launchScreen()
        composeTestRule.onNodeWithText("No workouts yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap + to create your first workout").assertIsDisplayed()
    }

    @Test
    fun topBar_showsFitTrackTitle() {
        launchScreen()
        composeTestRule.onNodeWithText("FitTrack").assertIsDisplayed()
    }

    @Test
    fun topBar_showsExercisesAndHistoryIcons() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Exercises").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("History").assertIsDisplayed()
    }

    @Test
    fun exercisesIcon_click_invokesCallback() {
        var clicked = false
        launchScreen(onExercisesClick = { clicked = true })
        composeTestRule.onNodeWithContentDescription("Exercises").performClick()
        assert(clicked)
    }

    @Test
    fun historyIcon_click_invokesCallback() {
        var clicked = false
        launchScreen(onHistoryClick = { clicked = true })
        composeTestRule.onNodeWithContentDescription("History").performClick()
        assert(clicked)
    }

    @Test
    fun fab_click_opensCreateDialog() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Create Workout").performClick()
        composeTestRule.onNodeWithText("Create Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout Name").assertIsDisplayed()
    }

    @Test
    fun createDialog_cancelButton_dismissesDialog() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Create Workout").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Create Workout").assertDoesNotExist()
    }

    @Test
    fun createDialog_createWithName_addsWorkout() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Create Workout").performClick()
        composeTestRule.onNodeWithText("Workout Name").performTextInput("Chest Day")
        composeTestRule.onAllNodesWithText("Create").filterToOne(hasText("Create")).performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("CHEST DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("CHEST DAY").assertIsDisplayed()
    }

    @Test
    fun workoutCard_showsPlayAndDeleteButtons() = runTest {
        db.workoutDao().insertWorkout(Workout(name = "PUSH DAY"))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("PUSH DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PUSH DAY").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Start").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test
    fun workoutCard_click_invokesOnWorkoutClick() = runTest {
        val id = db.workoutDao().insertWorkout(Workout(name = "BACK DAY"))
        var clickedId = -1L
        launchScreen(onWorkoutClick = { clickedId = it })
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("BACK DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("BACK DAY").performClick()
        assert(clickedId == id)
    }

    @Test
    fun workoutCard_startClick_invokesOnStartWorkout() = runTest {
        val id = db.workoutDao().insertWorkout(Workout(name = "LEG DAY"))
        var startedId = -1L
        launchScreen(onStartWorkout = { startedId = it })
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("LEG DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Start").performClick()
        assert(startedId == id)
    }

    @Test
    fun workoutCard_deleteClick_showsConfirmDialog() = runTest {
        db.workoutDao().insertWorkout(Workout(name = "SHOULDER DAY"))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("SHOULDER DAY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Delete").performClick()
        composeTestRule.onNodeWithText("Delete Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete \"SHOULDER DAY\"?").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_cancelButton_dismissesDialog() = runTest {
        db.workoutDao().insertWorkout(Workout(name = "ARMS"))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("ARMS").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Delete").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Delete Workout").assertDoesNotExist()
        composeTestRule.onNodeWithText("ARMS").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_confirmButton_removesWorkout() = runTest {
        db.workoutDao().insertWorkout(Workout(name = "TEMP WORKOUT"))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("TEMP WORKOUT").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Delete").performClick()
        composeTestRule.onAllNodesWithText("Delete").filterToOne(hasText("Delete")).performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("TEMP WORKOUT").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("TEMP WORKOUT").assertDoesNotExist()
    }
}
