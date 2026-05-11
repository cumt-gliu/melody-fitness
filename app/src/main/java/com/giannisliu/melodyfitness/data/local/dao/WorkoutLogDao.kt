package com.giannisliu.melodyfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.giannisliu.melodyfitness.data.local.entity.CardioEntryEntity
import com.giannisliu.melodyfitness.data.local.entity.StrengthExerciseEntity
import com.giannisliu.melodyfitness.data.local.entity.StrengthSetEntity
import com.giannisliu.melodyfitness.data.local.entity.WorkoutLogEntity
import com.giannisliu.melodyfitness.data.local.entity.WorkoutWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutLogDao {
    @Transaction
    @Query("SELECT * FROM workout_logs ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeWorkoutLogs(): Flow<List<WorkoutWithDetails>>
    @Query("SELECT COUNT(*) FROM workout_logs WHERE dateEpochDay >= :startEpochDay")
    fun observeWorkoutCountSince(startEpochDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM workout_logs WHERE dateEpochDay BETWEEN :startDay AND :endDay")
    fun observeWorkoutCountBetween(startDay: Long, endDay: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT dateEpochDay) FROM workout_logs WHERE dateEpochDay BETWEEN :startDay AND :endDay")
    fun observeTrainingDaysBetween(startDay: Long, endDay: Long): Flow<Int>

    @Insert
    suspend fun insertWorkoutLog(workoutLog: WorkoutLogEntity): Long

    @Insert
    suspend fun insertStrengthExercise(exercise: StrengthExerciseEntity): Long

    @Insert
    suspend fun insertStrengthSets(sets: List<StrengthSetEntity>)

    @Insert
    suspend fun insertCardioEntry(cardioEntry: CardioEntryEntity)

    @Query("UPDATE workout_logs SET title = :title, notes = :notes WHERE id = :workoutId")
    suspend fun updateWorkoutLogMetadata(
        workoutId: Long,
        title: String,
        notes: String,
    )

    @Query("DELETE FROM workout_logs WHERE id = :workoutId")
    suspend fun deleteWorkoutLogById(workoutId: Long)
}
