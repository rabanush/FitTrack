package com.fittrack.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val workoutsList: List<Workout> by viewModel.workouts.collectAsState()
    val (showCreateDialog, setShowCreateDialog) = remember { mutableStateOf(false) }
    val (newWorkoutName, setNewWorkoutName) = remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "TRAINING", 
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = onExercisesClick) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = "Exercises", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { setShowCreateDialog(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Workout")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (workoutsList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No workouts yet", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(workoutsList) { workout ->
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
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { setShowCreateDialog(false); setNewWorkoutName("") },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("New Workout Plan", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newWorkoutName,
                    onValueChange = setNewWorkoutName,
                    label = { Text("Name (e.g. Push, Pull, Legs)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWorkoutName.isNotBlank()) {
                            viewModel.createWorkout(newWorkoutName) {}
                            setNewWorkoutName("")
                            setShowCreateDialog(false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { setShowCreateDialog(false); setNewWorkoutName("") }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    val (workoutToDelete, setWorkoutToDelete) = remember { mutableStateOf<Workout?>(null) }

    workoutToDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { setWorkoutToDelete(null) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete Plan", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Do you want to delete \"${workout.name}\"?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteWorkout(workout); setWorkoutToDelete(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { setWorkoutToDelete(null) }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
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
    val (showDeleteConfirm, setShowDeleteConfirm) = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.FitnessCenter, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workout.name.uppercase(), 
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Workout Plan", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = onStart,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            
            IconButton(onClick = { setShowDeleteConfirm(true) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.3f))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { setShowDeleteConfirm(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete Plan", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Do you want to delete \"${workout.name}\"?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); setShowDeleteConfirm(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { setShowDeleteConfirm(false) }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}
