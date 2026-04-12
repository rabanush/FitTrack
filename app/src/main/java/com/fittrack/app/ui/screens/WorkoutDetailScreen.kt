package com.fittrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.viewmodel.WorkoutDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    viewModel: WorkoutDetailViewModel,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit
) {
    val workout by viewModel.workout.collectAsState()
    val exercises by viewModel.workoutExercises.observeAsState(emptyList())
    val allExercises by viewModel.allExercises.observeAsState(emptyList())

    var isEditingName by remember { mutableStateOf(false) }
    var workoutName by remember(workout?.name) { mutableStateOf(workout?.name ?: "") }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = workoutName,
                            onValueChange = { workoutName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(workout?.name ?: "Workout")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditingName) {
                        IconButton(onClick = {
                            viewModel.updateWorkoutName(workoutName)
                            isEditingName = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    } else {
                        IconButton(onClick = { isEditingName = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Name")
                        }
                    }
                    IconButton(onClick = onStartWorkout) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Workout", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExerciseDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (exercises.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No exercises added yet. Tap + to add exercises.")
                    }
                }
            }
            itemsIndexed(exercises) { index, item ->
                WorkoutExerciseItem(
                    item = item,
                    index = index,
                    total = exercises.size,
                    onDelete = { viewModel.removeExerciseFromWorkout(item.workoutExercise) },
                    onMoveUp = { if (index > 0) viewModel.reorderExercises(index, index - 1) },
                    onMoveDown = { if (index < exercises.size - 1) viewModel.reorderExercises(index, index + 1) },
                    onRestTimerChange = { newSecs -> viewModel.updateRestTimer(item.workoutExercise, newSecs) }
                )
            }
        }
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            allExercises = allExercises,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onAdd = { exercise ->
                viewModel.addExerciseToWorkout(exercise.id)
                showAddExerciseDialog = false
                searchQuery = ""
            },
            onDismiss = { showAddExerciseDialog = false; searchQuery = "" }
        )
    }
}

@Composable
fun WorkoutExerciseItem(
    item: WorkoutExerciseWithExercise,
    index: Int,
    total: Int,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRestTimerChange: (Int) -> Unit
) {
    var showTimerEdit by remember { mutableStateOf(false) }
    var timerValue by remember(item.workoutExercise.restTimerSeconds) {
        mutableStateOf(item.workoutExercise.restTimerSeconds.toString())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleSmall)
                Text(item.exercise.muscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    if (showTimerEdit) {
                        OutlinedTextField(
                            value = timerValue,
                            onValueChange = { timerValue = it },
                            label = { Text("Rest (s)") },
                            singleLine = true,
                            modifier = Modifier.width(80.dp)
                        )
                        IconButton(onClick = {
                            timerValue.toIntOrNull()?.let { onRestTimerChange(it) }
                            showTimerEdit = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save timer")
                        }
                    } else {
                        Text(
                            "${item.workoutExercise.restTimerSeconds}s rest",
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { showTimerEdit = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit timer", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Column {
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseDialog(
    allExercises: List<Exercise>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAdd: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = allExercises.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.muscleGroup.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered.size) { idx ->
                        val exercise = filtered[idx]
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(exercise.muscleGroup) },
                            trailingContent = {
                                IconButton(onClick = { onAdd(exercise) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
