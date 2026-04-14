package com.fittrack.app.fake

import com.fittrack.app.data.dao.LogEntryDao
import com.fittrack.app.data.model.LogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLogEntryDao : LogEntryDao {
    private val entries = mutableListOf<LogEntry>()
    private var nextId = 1L

    override fun getAllLogEntries(): Flow<List<LogEntry>> =
        MutableStateFlow(entries.sortedByDescending { it.date })

    override fun getLogEntriesForExercise(exerciseId: Long): Flow<List<LogEntry>> =
        MutableStateFlow(entries.filter { it.exerciseId == exerciseId }
            .sortedWith(compareByDescending<LogEntry> { it.date }.thenBy { it.setNumber }))

    override suspend fun getPreviousLogEntries(
        exerciseId: Long,
        workoutId: Long,
        beforeDate: Long
    ): List<LogEntry> {
        val maxDate = entries
            .filter { it.exerciseId == exerciseId && it.workoutId == workoutId && it.date < beforeDate }
            .maxOfOrNull { it.date } ?: return emptyList()
        return entries
            .filter { it.exerciseId == exerciseId && it.workoutId == workoutId && it.date == maxDate }
            .sortedBy { it.setNumber }
    }

    override suspend fun getPreviousLogEntriesForExercise(
        exerciseId: Long,
        beforeDate: Long
    ): List<LogEntry> {
        val maxDate = entries
            .filter { it.exerciseId == exerciseId && it.date < beforeDate }
            .maxOfOrNull { it.date } ?: return emptyList()
        return entries
            .filter { it.exerciseId == exerciseId && it.date == maxDate }
            .sortedBy { it.setNumber }
    }

    override fun getAllWorkoutDates(): Flow<List<Long>> =
        MutableStateFlow(entries.map { it.date }.distinct().sortedDescending())

    override fun getLogEntriesForDate(date: Long): Flow<List<LogEntry>> =
        MutableStateFlow(
            entries.filter { it.date == date }
                .sortedWith(compareBy({ it.workoutId }, { it.exerciseId }, { it.setNumber }))
        )

    override suspend fun insertLogEntry(logEntry: LogEntry): Long {
        val id = if (logEntry.id == 0L) nextId++ else logEntry.id
        entries.removeIf { it.id == id }
        entries.add(logEntry.copy(id = id))
        return id
    }

    override suspend fun insertLogEntries(logEntries: List<LogEntry>) {
        logEntries.forEach { insertLogEntry(it) }
    }

    override suspend fun deleteLogEntry(logEntry: LogEntry) {
        entries.removeIf { it.id == logEntry.id }
    }

    override suspend fun deleteLogEntriesForSession(exerciseId: Long, workoutId: Long, date: Long) {
        entries.removeIf {
            it.exerciseId == exerciseId && it.workoutId == workoutId && it.date == date
        }
    }
}
