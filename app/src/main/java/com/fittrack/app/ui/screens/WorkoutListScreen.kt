package com.fittrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.Workout
import com.fittrack.app.viewmodel.WorkoutListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    viewModel: WorkoutListViewModel,
    onWorkoutClick: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onExercisesClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val workouts by viewModel.workouts.observeAsState(emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var newWorkoutName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitTrack", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onExercisesClick) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = "Exercises")
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Workout")
            }
        }
    ) { padding ->
        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                    Text("No workouts yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap + to create your first workout", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workouts) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onClick = { onWorkoutClick(workout.id) },
                        onStart = { onStartWorkout(workout.id) },
                        onDelete = { viewModel.deleteWorkout(workout) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newWorkoutName = "" },
            title = { Text("Create Workout") },
            text = {
                OutlinedTextField(
                    value = newWorkoutName,
                    onValueChange = { newWorkoutName = it },
                    label = { Text("Workout Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newWorkoutName.isNotBlank()) {
                            viewModel.createWorkout(newWorkoutName) {}
                            newWorkoutName = ""
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newWorkoutName = "" }) {
                    Text("Cancel")
                }
            }
        )
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
                Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Workout") },
            text = { Text("Delete \"${workout.name}\"?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
