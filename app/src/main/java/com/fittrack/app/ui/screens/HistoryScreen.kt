package com.fittrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val dates by viewModel.allWorkoutDates.observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text("No workout history yet.")
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
                    HistoryDateCard(date = date, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun HistoryDateCard(date: Long, viewModel: HistoryViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val entriesLive = remember(date) { viewModel.getLogEntriesForDate(date) }
    val entries by entriesLive.observeAsState(emptyList())
    val dateStr = remember(date) {
        SimpleDateFormat("EEE, MMM d yyyy HH:mm", Locale.getDefault()).format(Date(date))
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
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                entries.groupBy { it.exerciseId }.forEach { (_, exerciseEntries) ->
                    ExerciseHistorySection(exerciseEntries)
                }
            }
        }
    }
}

@Composable
fun ExerciseHistorySection(entries: List<LogEntry>) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        entries.firstOrNull()?.let {
            Text("Exercise #${it.exerciseId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Set ${entry.setNumber}", style = MaterialTheme.typography.bodySmall)
                Text("${entry.weight}kg × ${entry.reps} reps, RIR: ${entry.rir}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
