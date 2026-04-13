package com.fittrack.app.ui.screens

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrack.app.viewmodel.ActiveWorkoutViewModel
import com.fittrack.app.viewmodel.ExerciseSessionData
import com.fittrack.app.viewmodel.SetData
import com.fittrack.app.viewmodel.TimerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onFinish: () -> Unit
) {
    val workout by viewModel.workout.collectAsState()
    val sessions by viewModel.exerciseSessions.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val (showFinishConfirm, setShowFinishConfirm) = remember { mutableStateOf(false) }
    val (showCancelConfirm, setShowCancelConfirm) = remember { mutableStateOf(false) }

<<<<<<< copilot/fix-timer-sound-issues
    // Fix 2: intercept hardware back button and show cancel dialog
=======
    // Intercept system back button - show the cancel dialog instead of leaving silently
>>>>>>> main
    BackHandler {
        setShowCancelConfirm(true)
    }

<<<<<<< copilot/fix-timer-sound-issues
    // Fix 3: only show exercises that still have incomplete sets
    val visibleSessions = remember(sessions) {
        sessions.mapIndexed { index, session -> index to session }
            .filter { (_, session) -> session.sets.any { !it.isCompleted } }
    }
=======
    // Only show exercises that still have at least one incomplete set
    val visibleSessions = sessions.filter { session -> !session.sets.all { it.isCompleted } }
>>>>>>> main

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "Active Workout", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { setShowCancelConfirm(true) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (timerState.isRunning || timerState.remainingSeconds > 0) {
                item {
                    RestTimerBanner(
                        timerState = timerState,
                        onSkip = { viewModel.skipTimer() },
                        onAdd15 = { viewModel.adjustTimer(15) },
                        onMinus15 = { viewModel.adjustTimer(-15) }
                    )
                }
            }

<<<<<<< copilot/fix-timer-sound-issues
            itemsIndexed(visibleSessions) { _, (sessionIndex, session) ->
=======
            itemsIndexed(visibleSessions) { _, session ->
                val exerciseIndex = sessions.indexOf(session)
>>>>>>> main
                ExerciseSessionCard(
                    session = session,
                    onAddSet = { viewModel.addSet(sessionIndex) },
                    onRemoveSet = { viewModel.removeSet(sessionIndex) },
                    onUpdateSet = { setIndex, weight, reps ->
                        viewModel.updateSetData(sessionIndex, setIndex, weight, reps)
                    },
                    onCompleteSet = { setIndex ->
                        viewModel.completeSet(sessionIndex, setIndex)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { setShowFinishConfirm(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("FINISH WORKOUT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { setShowFinishConfirm(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Finish Workout", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Save and finish this workout session?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishWorkout { onFinish() }
                        setShowFinishConfirm(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Finish", color = MaterialTheme.colorScheme.onPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { setShowFinishConfirm(false) }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { setShowCancelConfirm(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Cancel Workout", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Do you really want to cancel this workout? All progress for this session will be lost.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        setShowCancelConfirm(false)
                        onFinish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = {
                TextButton(onClick = { setShowCancelConfirm(false) }) {
                    Text("Keep Training", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
fun RestTimerBanner(
    timerState: TimerState,
    onSkip: () -> Unit,
    onAdd15: () -> Unit,
    onMinus15: () -> Unit
) {
    val progress = if (timerState.totalSeconds > 0)
        timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat()
    else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Resting", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(
                formatTime(timerState.remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onMinus15) { Text("-15s", color = Color.Gray) }
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Skip Rest") }
                TextButton(onClick = onAdd15) { Text("+15s", color = Color.Gray) }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "%d".format(s)
}

@Composable
fun ExerciseSessionCard(
    session: ExerciseSessionData,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onUpdateSet: (Int, String?, String?) -> Unit,
    onCompleteSet: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = session.workoutExercise.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = session.workoutExercise.exercise.muscleGroup,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("PREVIOUS", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("KG", modifier = Modifier.width(60.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("REPS", modifier = Modifier.width(60.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(Modifier.width(40.dp))
            }

            session.sets.forEachIndexed { setIndex, set ->
                SetRow(
                    set = set,
                    onWeightChange = { onUpdateSet(setIndex, it, null) },
                    onRepsChange = { onUpdateSet(setIndex, null, it) },
                    onComplete = { onCompleteSet(setIndex) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRemoveSet,
                    enabled = session.sets.size > 1
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("REMOVE SET", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onAddSet) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ADD SET", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun SetRow(
    set: SetData,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onComplete: () -> Unit
) {
<<<<<<< copilot/fix-timer-sound-issues
    // Fix 5: allow completing when a placeholder (prevReps) exists even if the field is empty
    val hasReps = set.reps.isNotBlank() && (set.reps.toIntOrNull() ?: 0) > 0
    val hasPrevReps = set.prevReps.isNotBlank() && (set.prevReps.toIntOrNull() ?: 0) > 0
    val canComplete = !set.isCompleted && (hasReps || hasPrevReps)
    // Fix 4: also allow clicking to un-complete a set
    val canInteract = canComplete || set.isCompleted
=======
    // Allow completing when actual reps are entered OR when a ghost/placeholder value exists
    val hasReps = set.reps.isNotBlank() && (set.reps.toIntOrNull() ?: 0) > 0
    val hasGhostReps = set.reps.isBlank() && set.prevReps.isNotBlank() && (set.prevReps.toIntOrNull() ?: 0) > 0
    val canComplete = hasReps || hasGhostReps
>>>>>>> main
    val tintColor = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = tintColor
        )
        
        Text(
            text = if (set.prevWeight.isNotEmpty()) "${set.prevWeight}kg × ${set.prevReps}" else "-",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            SetInputField(
                value = set.weight,
                placeholder = set.prevWeight,
                onValueChange = onWeightChange,
<<<<<<< copilot/fix-timer-sound-issues
                enabled = true  // Fix 4: always allow editing weight
=======
                enabled = true
>>>>>>> main
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))

        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            SetInputField(
                value = set.reps,
                placeholder = set.prevReps,
                onValueChange = onRepsChange,
<<<<<<< copilot/fix-timer-sound-issues
                enabled = true  // Fix 4: always allow editing reps
=======
                enabled = true
>>>>>>> main
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onComplete,
            enabled = canInteract,  // Fix 4: clickable to toggle; Fix 5: clickable with ghost numbers
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (set.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Complete",
                tint = when {
                    set.isCompleted -> MaterialTheme.colorScheme.primary
                    canComplete -> MaterialTheme.colorScheme.onSurface
                    else -> Color.Gray.copy(alpha = 0.3f)
                }
            )
        }
    }
}

@Composable
fun SetInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { 
            Text(
                placeholder, 
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            ) 
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
