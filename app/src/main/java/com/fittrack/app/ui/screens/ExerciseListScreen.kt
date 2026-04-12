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
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.viewmodel.ExerciseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    viewModel: ExerciseViewModel,
    onBack: () -> Unit
) {
    val exercises by viewModel.allExercises.observeAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = exercises.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.muscleGroup.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise Database") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val grouped = filtered.groupBy { it.muscleGroup }
                grouped.forEach { (group, exList) ->
                    item {
                        Text(
                            group,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(exList) { exercise ->
                        ExerciseItem(
                            exercise = exercise,
                            onEdit = { editingExercise = exercise },
                            onDelete = {
                                if (exercise.isCustom) viewModel.deleteExercise(exercise)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingExercise != null) {
        ExerciseDialog(
            exercise = editingExercise,
            onSave = { name, muscleGroup ->
                if (editingExercise != null) {
                    viewModel.updateExercise(editingExercise!!.copy(name = name, muscleGroup = muscleGroup))
                } else {
                    viewModel.insertExercise(name, muscleGroup, isCustom = true)
                }
                showAddDialog = false
                editingExercise = null
            },
            onDismiss = { showAddDialog = false; editingExercise = null }
        )
    }
}

@Composable
fun ExerciseItem(
    exercise: Exercise,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                if (exercise.isCustom) {
                    Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            if (exercise.isCustom) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ExerciseDialog(
    exercise: Exercise?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(exercise) { mutableStateOf(exercise?.name ?: "") }
    var muscleGroup by remember(exercise) { mutableStateOf(exercise?.muscleGroup ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exercise == null) "Add Exercise" else "Edit Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = muscleGroup,
                    onValueChange = { muscleGroup = it },
                    label = { Text("Muscle Group") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && muscleGroup.isNotBlank()) onSave(name, muscleGroup) },
                enabled = name.isNotBlank() && muscleGroup.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
