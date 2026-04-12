package com.fittrack.app.ui.screens

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 600)
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "Active Workout") },
                navigationIcon = {
                    IconButton(onClick = { showFinishConfirm = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Finish")
                    }
                },
                actions = {
                    Button(
                        onClick = { showFinishConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Finish")
                    }
                }
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

            itemsIndexed(sessions) { exerciseIndex, session ->
                ExerciseSessionCard(
                    session = session,
                    exerciseIndex = exerciseIndex,
                    onAddSet = { viewModel.addSet(exerciseIndex) },
                    onRemoveSet = { viewModel.removeSet(exerciseIndex) },
                    onUpdateSet = { setIndex, weight, reps, rir ->
                        viewModel.updateSetData(exerciseIndex, setIndex, weight, reps, rir)
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
            title = { Text("Finish Workout") },
            text = { Text("Save and finish this workout?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.finishWorkout { onFinish() }
                    showFinishConfirm = false
                }) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Cancel") }
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

    val bgColor by animateColorAsState(
        targetValue = when {
            timerState.remainingSeconds <= 3 -> MaterialTheme.colorScheme.errorContainer
            timerState.remainingSeconds <= 10 -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "timer_bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Rest Timer",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                formatTime(timerState.remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onMinus15) { Text("-15s") }
                Button(onClick = onSkip) { Text("Skip") }
                OutlinedButton(onClick = onAdd15) { Text("+15s") }
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
    onUpdateSet: (Int, String?, String?, String?) -> Unit,
    onCompleteSet: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = session.workoutExercise.exercise.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = session.workoutExercise.exercise.muscleGroup,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Set", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            Text("Previous", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            Text("kg", modifier = Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            Text("Reps", modifier = Modifier.width(56.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            Text("RIR", modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(40.dp))
        }

        HorizontalDivider()

        session.sets.forEachIndexed { setIndex, set ->
            val prevSet = session.previousSets.getOrNull(setIndex)
            SetRow(
                set = set,
                setIndex = setIndex,
                previousSet = prevSet,
                onWeightChange = { onUpdateSet(setIndex, it, null, null) },
                onRepsChange = { onUpdateSet(setIndex, null, it, null) },
                onRirChange = { onUpdateSet(setIndex, null, null, it) },
                onComplete = { onCompleteSet(setIndex) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRemoveSet,
                modifier = Modifier.weight(1f),
                enabled = session.sets.size > 1
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Remove Set")
            }
            Button(
                onClick = onAddSet,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Set")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun SetRow(
    set: SetData,
    setIndex: Int,
    previousSet: com.fittrack.app.data.model.LogEntry?,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRirChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
        label = "set_bg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        // Previous performance
        Text(
            text = if (previousSet != null) "${previousSet.weight}kg × ${previousSet.reps}" else "-",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = set.weight,
            onValueChange = onWeightChange,
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            enabled = !set.isCompleted
        )
        OutlinedTextField(
            value = set.reps,
            onValueChange = onRepsChange,
            modifier = Modifier.width(56.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            enabled = !set.isCompleted
        )
        OutlinedTextField(
            value = set.rir,
            onValueChange = onRirChange,
            modifier = Modifier.width(48.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            enabled = !set.isCompleted
        )
        IconButton(onClick = onComplete, enabled = !set.isCompleted, modifier = Modifier.size(40.dp)) {
            Icon(
                if (set.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Complete",
                tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
