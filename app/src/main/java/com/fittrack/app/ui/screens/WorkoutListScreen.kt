package com.fittrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.Workout
import com.fittrack.app.ui.components.DeleteConfirmDialog
import com.fittrack.app.ui.components.NameInputDialog
import com.fittrack.app.ui.screens.food.FoodTrackerScreen
import com.fittrack.app.viewmodel.FoodTrackerViewModel
import com.fittrack.app.viewmodel.WorkoutListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    viewModel: WorkoutListViewModel,
    foodTrackerViewModel: FoodTrackerViewModel,
    onWorkoutClick: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onExercisesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAddFood: (mealId: Long, mealName: String) -> Unit,
    onAddRecipeToMeal: (mealId: Long, mealName: String) -> Unit,
    onRecipesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val currentPage = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()
    var foodTabSessionKey by rememberSaveable { mutableIntStateOf(0) }
    var previousPage by rememberSaveable { mutableIntStateOf(currentPage) }
    var pendingExpandMealId by rememberSaveable { mutableStateOf<Long?>(null) }
    var preserveMealExpansionOnReturn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        if (previousPage != 1 && currentPage == 1) {
            if (preserveMealExpansionOnReturn) {
                preserveMealExpansionOnReturn = false
            } else {
                foodTabSessionKey++
            }
        }
        previousPage = currentPage
    }

    // Dialog state for "Create Workout" (workout page FAB)
    var showCreateWorkoutDialog by remember { mutableStateOf(false) }

    val workouts by viewModel.workouts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitTrack", fontWeight = FontWeight.Bold) },
                actions = {
                    if (currentPage == 0) {
                        IconButton(onClick = onExercisesClick) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = "Übungen")
                        }
                        IconButton(onClick = onHistoryClick) {
                            Icon(Icons.Default.History, contentDescription = "Verlauf")
                        }
                    } else {
                        IconButton(onClick = onRecipesClick) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Rezepte verwalten")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentPage == 0) {
                FloatingActionButton(
                    onClick = { showCreateWorkoutDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = currentPage) {
                listOf("Workout" to Icons.Default.FitnessCenter, "Ernährung" to Icons.Default.Restaurant)
                    .forEachIndexed { index, (title, icon) ->
                        Tab(
                            selected = currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) },
                            icon = { Icon(icon, contentDescription = null) }
                        )
                    }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WorkoutListContent(
                        workouts = workouts,
                        onWorkoutClick = onWorkoutClick,
                        onStartWorkout = onStartWorkout,
                        onDelete = { viewModel.deleteWorkout(it) }
                    )
                    1 -> FoodTrackerScreen(
                        viewModel = foodTrackerViewModel,
                        sessionKey = foodTabSessionKey,
                        pendingExpandMealId = pendingExpandMealId,
                        onPendingExpandHandled = { handledMealId ->
                            if (pendingExpandMealId == handledMealId) {
                                pendingExpandMealId = null
                            }
                        },
                        onAddFood = { mealId, mealName ->
                            pendingExpandMealId = mealId
                            preserveMealExpansionOnReturn = true
                            onAddFood(mealId, mealName)
                        },
                        onAddRecipeToMeal = { mealId, mealName ->
                            pendingExpandMealId = mealId
                            preserveMealExpansionOnReturn = true
                            onAddRecipeToMeal(mealId, mealName)
                        }
                    )
                }
            }
        }
    }

    // "Create Workout" dialog
    if (showCreateWorkoutDialog) {
        NameInputDialog(
            title = "Workout erstellen",
            label = "Workout Name",
            onDismiss = { showCreateWorkoutDialog = false },
            onConfirm = { name ->
                viewModel.createWorkout(name) {}
                showCreateWorkoutDialog = false
            }
        )
    }
}

@Composable
private fun WorkoutListContent(
    workouts: List<Workout>,
    onWorkoutClick: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onDelete: (Workout) -> Unit
) {
    if (workouts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Noch keine Workouts", style = MaterialTheme.typography.titleMedium)
                Text("Tippe + um ein Workout zu erstellen", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(workouts) { workout ->
                WorkoutCard(
                    workout = workout,
                    onClick = { onWorkoutClick(workout.id) },
                    onStart = { onStartWorkout(workout.id) },
                    onDelete = { onDelete(workout) }
                )
            }
        }
    }
}

@Composable
fun WorkoutCard(
    workout: Workout,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onStart) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Starten", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = "Workout löschen",
            message = "\"${workout.name}\" löschen?",
            onConfirm = { onDelete(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
