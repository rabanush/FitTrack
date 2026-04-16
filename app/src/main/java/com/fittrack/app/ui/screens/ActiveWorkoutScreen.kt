package com.fittrack.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrack.app.viewmodel.ActiveWorkoutViewModel
import com.fittrack.app.viewmodel.ExerciseSessionData
import com.fittrack.app.viewmodel.SetData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onFinish: () -> Unit
) {
    val workout by viewModel.workout.collectAsState()
    val sessions by viewModel.exerciseSessions.collectAsState()
    val workoutElapsedSeconds by viewModel.workoutElapsedSeconds.collectAsState()
    
    // Collect only a boolean flag so that the 200 ms timer tick does NOT recompose the entire screen.
    // The actual countdown is handled inside RestTimerBanner, which is the only component that recomposes on each tick.
    val isTimerVisible by viewModel.isTimerVisible.collectAsState()
    
    var showFinishConfirm by remember { mutableStateOf(false) }

    // Intercept system back press
    BackHandler { showFinishConfirm = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "Active Workout", color = Color.White) },
                actions = {
                    Text(
                        text = formatElapsedWorkoutTime(workoutElapsedSeconds),
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Button(
                onClick = { showFinishConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("FINISH", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        // Column + verticalScroll is preferred over LazyColumn here: a workout has at most ~15
        // exercises, so eager composition is negligible. LazyColumn's per-scroll-frame
        // lazy-composition of TextField-heavy cards was the primary source of scroll jank.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 32.dp)
        ) {
            // Timer section - isolated recomposition
            if (isTimerVisible) {
                RestTimerBanner(
                    viewModel = viewModel,
                    onSkip = { viewModel.skipTimer() },
                    onAdd15 = { viewModel.adjustTimer(15) },
                    onMinus15 = { viewModel.adjustTimer(-15) }
                )
            }

            sessions.forEachIndexed { index, session ->
                if (!session.sets.all { it.isCompleted }) {
                    ExerciseSessionCard(
                        session = session,
                        onAddSet = { viewModel.addSet(index) },
                        onRemoveSet = { viewModel.removeSet(index) },
                        onUpdateSet = { setIndex, weight, reps ->
                            viewModel.updateSetData(index, setIndex, weight, reps)
                        },
                        onCompleteSet = { setIndex -> viewModel.completeSet(index, setIndex) }
                    )
                }
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
    viewModel: ActiveWorkoutViewModel,
    onSkip: () -> Unit,
    onAdd15: () -> Unit,
    onMinus15: () -> Unit
) {
    // Collecting the timer state here isolates recomposition to this component
    val timerState by viewModel.timerState.collectAsState()
    
    val progress = remember(timerState.remainingSeconds, timerState.totalSeconds) {
        if (timerState.totalSeconds > 0)
            timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat()
        else 0f
    }

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
                text = formatTime(timerState.remainingSeconds),
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

private fun formatElapsedWorkoutTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%02d:%02d".format(minutes, secs)
}

@Composable
fun ExerciseSessionCard(
    session: ExerciseSessionData,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onUpdateSet: (Int, String?, String?) -> Unit,
    onCompleteSet: (Int) -> Unit
) {
    val exercise = session.workoutExercise.exercise

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = exercise.muscleGroup,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

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

            session.sets.forEachIndexed { index, set ->
                SetRow(
                    set = set,
                    onWeightChange = { onUpdateSet(index, it, null) },
                    onRepsChange = { onUpdateSet(index, null, it) },
                    onComplete = { onCompleteSet(index) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRemoveSet, enabled = session.sets.size > 1) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("REMOVE SET")
                }
                TextButton(onClick = onAddSet) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ADD SET")
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
    val tintColor = if (set.isCompleted) MaterialTheme.colorScheme.primary else Color.White
    val focusManager = LocalFocusManager.current
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${set.setNumber}",
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

        Box(modifier = Modifier.width(60.dp)) {
            SetInputField(
                committed = set.weight,
                placeholder = set.prevWeight,
                onCommit = onWeightChange,
                isCompleted = set.isCompleted
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))

        Box(modifier = Modifier.width(60.dp)) {
            SetInputField(
                committed = set.reps,
                placeholder = set.prevReps,
                onCommit = onRepsChange,
                isCompleted = set.isCompleted
            )
        }

        IconButton(
            onClick = {
                focusManager.clearFocus(force = true)
                onComplete()
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (set.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun SetInputField(
    committed: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    isCompleted: Boolean
) {
    // Local draft – keeps keystrokes off the ViewModel state until focus leaves the field,
    // which prevents per-keystroke list recompositions.
    var draft by remember { mutableStateOf(committed) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Sync external changes (e.g. "complete set" back-fills prevWeight) only while
    // the field is not focused, so we never clobber text the user is actively typing.
    LaunchedEffect(committed) {
        if (!isFocused) {
            draft = committed
        }
    }

    TextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = { Text(placeholder, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                if (draft != committed) onCommit(draft)
                focusManager.clearFocus(force = true)
            }
        ),
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = if (isCompleted) MaterialTheme.colorScheme.primary else Color.White
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused && draft != committed) {
                    onCommit(draft)
                }
                isFocused = focusState.isFocused
            }
    )
}
