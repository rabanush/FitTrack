package com.fittrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val dates by viewModel.allWorkoutDates.collectAsState()
    val exerciseNames by viewModel.exerciseNames.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trainingshistorie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        if (dates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Noch keine Trainingseinträge.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dates) { date ->
                    HistoryDateCard(
                        date = date,
                        viewModel = viewModel,
                        exerciseNames = exerciseNames
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryDateCard(
    date: Long,
    viewModel: HistoryViewModel,
    exerciseNames: Map<Long, String>
) {
    var expanded by remember { mutableStateOf(false) }
    val entriesFlow = remember(date) { viewModel.getLogEntriesForDate(date) }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val dateStr = remember(date) {
        SimpleDateFormat("EEE, d. MMM yyyy", Locale.getDefault()).format(Date(date))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dateStr,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ausblenden" else "Einblenden")
                }
            }
            if (expanded) {
                entries.groupBy { it.exerciseId }.forEach { (exerciseId, exerciseEntries) ->
                    ExerciseHistorySection(
                        entries = exerciseEntries,
                        exerciseName = exerciseNames[exerciseId] ?: "Übung #$exerciseId"
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseHistorySection(entries: List<LogEntry>, exerciseName: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Satz ${entry.setNumber}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${entry.weight} kg × ${entry.reps} Wdh., RIR: ${entry.rir}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
