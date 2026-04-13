package com.fittrack.app.ui.screens

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    var showFinishConfirm by remember { mutableStateOf(false) }

    // Beep when timer reaches 0
    LaunchedEffect(timerState.remainingSeconds, timerState.isRunning) {
        if (!timerState.isRunning && timerState.remainingSeconds == 0 && timerState.totalSeconds > 0) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                try {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
                    kotlinx.coroutines.delay(900L)
                } finally {
                    toneGen.release()
                }
            } catch (_: Exception) {}
        }
    }

    // Intercept system back press – show confirmation instead of silently leaving
    BackHandler { showFinishConfirm = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "Active Workout", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { showFinishConfirm = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showFinishConfirm = true }
                    ) {
                        Text("FINISH", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Timer overlay
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

            // Only show exercises that still have incomplete sets
            val activeIndices = sessions.indices.filter { i ->
                !sessions[i].sets.all { it.isCompleted }
            }

            itemsIndexed(activeIndices) { _, exerciseIndex ->
                val session = sessions[exerciseIndex]
                ExerciseSessionCard(
                    session = session,
                    exerciseIndex = exerciseIndex,
                    onAddSet = { viewModel.addSet(exerciseIndex) },
                    onRemoveSet = { viewModel.removeSet(exerciseIndex) },
                    onUpdateSet = { setIndex, weight, reps ->
                        viewModel.updateSetData(exerciseIndex, setIndex, weight, reps)
                    },
                    onCompleteSet = { setIndex ->
                        viewModel.completeSet(exerciseIndex, setIndex)
                    }
                )
            }
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Finish Workout", color = Color.White) },
            text = { Text("Save and finish this workout session?", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishWorkout { onFinish() }
                        showFinishConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { 
                    Text("Cancel", color = MaterialTheme.colorScheme.primary) 
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

    val bgColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Resting",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Text(
                formatTime(timerState.remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
    exerciseIndex: Int,
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
                color = Color.White
            )
            Text(
                text = session.workoutExercise.exercise.muscleGroup,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
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
                TextButton(
                    onClick = onAddSet
                ) {
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
    val completedBg = if (set.isCompleted)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    else
        Color.Transparent
    val tintColor = if (set.isCompleted) MaterialTheme.colorScheme.primary else Color.White
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(completedBg, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = tintColor
        )
        
        // Previous performance as hint
        Text(
            text = if (set.prevWeight.isNotEmpty()) "${set.prevWeight}kg × ${set.prevReps}" else "-",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        // Inputs – always enabled so completed sets remain editable
        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            SetInputField(
                value = set.weight,
                placeholder = set.prevWeight,
                onValueChange = onWeightChange,
                isCompleted = set.isCompleted
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))

        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            SetInputField(
                value = set.reps,
                placeholder = set.prevReps,
                onValueChange = onRepsChange,
                isCompleted = set.isCompleted
            )
        }

        // Complete / un-complete toggle button – always enabled
        IconButton(
            onClick = onComplete,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (set.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (set.isCompleted) "Unmark complete" else "Mark complete",
                tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SetInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isCompleted: Boolean = false
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
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            color = if (isCompleted) MaterialTheme.colorScheme.primary else Color.White
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.3f),
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
