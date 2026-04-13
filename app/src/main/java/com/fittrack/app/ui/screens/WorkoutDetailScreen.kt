package com.fittrack.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val exercises by viewModel.workoutExercises.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()

    val (isEditingName, setIsEditingName) = remember { mutableStateOf(false) }
    val (workoutName, setWorkoutName) = remember(workout?.name) { mutableStateOf(workout?.name ?: "") }
    val (showAddExerciseDialog, setShowAddExerciseDialog) = remember { mutableStateOf(false) }
    val (searchQuery, setSearchQuery) = remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = workoutName,
                            onValueChange = setWorkoutName,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    } else {
                        Text(
                            workout?.name?.uppercase() ?: "WORKOUT",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (isEditingName) {
                        IconButton(onClick = {
                            viewModel.updateWorkoutName(workoutName)
                            setIsEditingName(false)
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { setIsEditingName(true) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    IconButton(onClick = onStartWorkout) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Workout", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { setShowAddExerciseDialog(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (exercises.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No exercises added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(
                    items = exercises,
                    key = { _, item -> item.workoutExercise.id }
                ) { index, item ->
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
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            allExercises = allExercises,
            searchQuery = searchQuery,
            onSearchChange = setSearchQuery,
            onAdd = { exercise ->
                viewModel.addExerciseToWorkout(exercise.id)
                setShowAddExerciseDialog(false)
                setSearchQuery("")
            },
            onDismiss = { setShowAddExerciseDialog(false); setSearchQuery("") }
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
    val (showTimerEdit, setShowTimerEdit) = remember { mutableStateOf(false) }
    val (timerValue, setTimerValue) = remember(item.workoutExercise.restTimerSeconds) {
        mutableStateOf(item.workoutExercise.restTimerSeconds.toString())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                item.exercise.germanName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    item.exercise.muscleGroup,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    if (showTimerEdit) {
                        OutlinedTextField(
                            value = timerValue,
                            onValueChange = setTimerValue,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.width(70.dp).height(48.dp)
                        )
                        IconButton(onClick = {
                            timerValue.toIntOrNull()?.let { onRestTimerChange(it) }
                            setShowTimerEdit(false)
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Text(
                            "${item.workoutExercise.restTimerSeconds}s rest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { setShowTimerEdit(true) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit timer", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if(index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.3f))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if(index < total - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.3f))
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
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
                (it.germanName?.contains(searchQuery, ignoreCase = true) ?: false) ||
                it.muscleGroup.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add Exercise", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search (English or German)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered.size) { idx ->
                        val exercise = filtered[idx]
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(exercise.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                            supportingContent = {
                                Column {
                                    exercise.germanName?.let {
                                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(exercise.muscleGroup, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { onAdd(exercise) },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.primary) }
        }
    )
}
