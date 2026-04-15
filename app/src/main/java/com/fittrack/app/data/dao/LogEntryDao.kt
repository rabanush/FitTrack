package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.LogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY date DESC")
    fun getAllLogEntries(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE exercise_id = :exerciseId ORDER BY date DESC, set_number ASC")
    fun getLogEntriesForExercise(exerciseId: Long): Flow<List<LogEntry>>

    @Query("""
        SELECT * FROM log_entries 
        WHERE exercise_id = :exerciseId 
        AND workout_id = :workoutId
        AND date = (
            SELECT MAX(date) FROM log_entries 
            WHERE exercise_id = :exerciseId 
            AND workout_id = :workoutId
            AND date < :beforeDate
        )
        ORDER BY set_number ASC
    """)
    suspend fun getPreviousLogEntries(exerciseId: Long, workoutId: Long, beforeDate: Long): List<LogEntry>

    @Query("""
        SELECT * FROM log_entries 
        WHERE exercise_id = :exerciseId
        AND date = (
            SELECT MAX(date) FROM log_entries 
            WHERE exercise_id = :exerciseId
            AND date < :beforeDate
        )
        ORDER BY set_number ASC
    """)
    suspend fun getPreviousLogEntriesForExercise(exerciseId: Long, beforeDate: Long): List<LogEntry>

    @Query("""
        WITH latest AS (
            SELECT exercise_id, MAX(date) AS max_date
            FROM log_entries
            WHERE exercise_id IN (:exerciseIds) AND date < :beforeDate
            GROUP BY exercise_id
        )
        SELECT l.* FROM log_entries l
        INNER JOIN latest ON l.exercise_id = latest.exercise_id AND l.date = latest.max_date
        ORDER BY l.exercise_id, l.set_number ASC
    """)
    suspend fun getPreviousLogEntriesForExercises(exerciseIds: List<Long>, beforeDate: Long): List<LogEntry>

    @Query("SELECT DISTINCT date FROM log_entries ORDER BY date DESC")
    fun getAllWorkoutDates(): Flow<List<Long>>

    @Query("SELECT * FROM log_entries WHERE date = :date ORDER BY workout_id, exercise_id, set_number")
    fun getLogEntriesForDate(date: Long): Flow<List<LogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogEntry(logEntry: LogEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogEntries(logEntries: List<LogEntry>)

    @Delete
    suspend fun deleteLogEntry(logEntry: LogEntry)

    @Query("DELETE FROM log_entries WHERE exercise_id = :exerciseId AND workout_id = :workoutId AND date = :date")
    suspend fun deleteLogEntriesForSession(exerciseId: Long, workoutId: Long, date: Long)

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM log_entries ORDER BY date ASC")
    suspend fun getAllLogEntriesSync(): List<LogEntry>
}
