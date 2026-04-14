package com.fittrack.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.viewmodel.ExerciseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    viewModel: ExerciseViewModel,
    onBack: () -> Unit
) {
    val exercises by viewModel.allExercises.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = exercises.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.muscleGroup.contains(searchQuery, ignoreCase = true) ||
                it.germanName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Exercise Library", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { /* Info action */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar inspired by the clean look
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search exercises...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val grouped = filtered.groupBy { it.muscleGroup }
                grouped.forEach { (group, exList) ->
                    item(key = group) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp, 16.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                group.uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                                ),
                                color = Color.Gray
                            )
                        }
                    }
                    items(exList, key = { it.id }) { exercise ->
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
                    editingExercise = null
                } else {
                    viewModel.insertExercise(name, muscleGroup, isCustom = true)
                    showAddDialog = false
                }
            },
            onDismiss = { 
                showAddDialog = false
                editingExercise = null 
            }
        )
    }
}

@Composable
fun ExerciseItem(
    exercise: Exercise,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Illustration placeholder
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                if (exercise.germanName.isNotEmpty()) {
                    Text(
                        exercise.germanName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
                Text(
                    exercise.muscleGroup,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (exercise.isCustom) "Custom Exercise" else "Standard",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Edit", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (exercise.isCustom) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete", 
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Text(
                if (exercise == null) "New Exercise" else "Edit Exercise",
                color = Color.White,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = muscleGroup,
                    onValueChange = { muscleGroup = it },
                    label = { Text("Muscle Group") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && muscleGroup.isNotBlank()) onSave(name, muscleGroup) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && muscleGroup.isNotBlank()
            ) { Text("Save", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = MaterialTheme.colorScheme.primary) 
            }
        }
    )
}
