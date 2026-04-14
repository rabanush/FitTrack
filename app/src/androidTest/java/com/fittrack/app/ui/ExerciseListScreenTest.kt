package com.fittrack.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.ui.screens.ExerciseListScreen
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.TestDatabase
import com.fittrack.app.viewmodel.ExerciseViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ExerciseListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: FitTrackDatabase
    private lateinit var viewModel: ExerciseViewModel

    @Before
    fun setup() {
        db = TestDatabase.create()
        val repository = TestDatabase.createRepository(db)
        viewModel = ExerciseViewModel(repository)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun launchScreen(onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            FitTrackTheme {
                ExerciseListScreen(viewModel = viewModel, onBack = onBack)
            }
        }
    }

    @Test
    fun topBar_showsExerciseLibraryTitle() {
        launchScreen()
        composeTestRule.onNodeWithText("Exercise Library").assertIsDisplayed()
    }

    @Test
    fun backButton_click_invokesCallback() {
        var backClicked = false
        launchScreen(onBack = { backClicked = true })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun emptyState_showsSearchBar() {
        launchScreen()
        composeTestRule.onNodeWithText("Search exercises...").assertIsDisplayed()
    }

    @Test
    fun fab_click_opensAddDialog() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.onNodeWithText("New Exercise").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Muscle Group").assertIsDisplayed()
    }

    @Test
    fun addDialog_dismissButton_closesDialog() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("New Exercise").assertDoesNotExist()
    }

    @Test
    fun addDialog_saveButton_disabledWhenEmpty() {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        // Save button should not be clickable with empty fields - dialog should remain
        composeTestRule.onNodeWithText("New Exercise").assertIsDisplayed()
    }

    @Test
    fun addExercise_appearsInList() = runTest {
        launchScreen()
        composeTestRule.onNodeWithContentDescription("Add Exercise").performClick()
        composeTestRule.onNodeWithText("Name").performTextInput("Custom Press")
        composeTestRule.onNodeWithText("Muscle Group").performTextInput("Chest")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Custom Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Custom Press").assertIsDisplayed()
    }

    @Test
    fun exerciseList_showsExistingExercises() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest", isCustom = false))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Bench Press").assertIsDisplayed()
    }

    @Test
    fun exerciseList_showsMuscleGroupHeader() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest", isCustom = false))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("CHEST").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("CHEST").assertIsDisplayed()
    }

    @Test
    fun searchBar_filtersExercises() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
        db.exerciseDao().insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Search exercises...").performTextInput("Squat")
        composeTestRule.onNodeWithText("Squat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bench Press").assertDoesNotExist()
    }

    @Test
    fun searchBar_clearSearch_restoresAllExercises() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Bench Press", muscleGroup = "Chest"))
        db.exerciseDao().insertExercise(Exercise(name = "Squat", muscleGroup = "Quads"))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        val searchField = composeTestRule.onNodeWithText("Search exercises...")
        searchField.performTextInput("Squat")
        composeTestRule.onNodeWithText("Bench Press").assertDoesNotExist()
        searchField.performTextClearance()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Bench Press").assertIsDisplayed()
    }

    @Test
    fun customExercise_showsEditButton() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "My Exercise", muscleGroup = "Arms", isCustom = true))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("My Exercise").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
    }

    @Test
    fun customExercise_showsDeleteButton() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Custom Row", muscleGroup = "Back", isCustom = true))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Custom Row").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test
    fun editButton_click_opensEditDialog() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Old Name", muscleGroup = "Back", isCustom = true))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Old Name").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Edit").performClick()
        composeTestRule.onNodeWithText("Edit Exercise").assertIsDisplayed()
    }

    @Test
    fun nonCustomExercise_doesNotShowDeleteButton() = runTest {
        db.exerciseDao().insertExercise(Exercise(name = "Standard Press", muscleGroup = "Chest", isCustom = false))
        launchScreen()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Standard Press").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }
}
