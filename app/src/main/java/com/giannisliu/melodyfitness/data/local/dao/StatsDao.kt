package com.giannisliu.melodyfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM cardio_entries")
    fun observeTotalCardioMinutes(): Flow<Int>

    @Query("SELECT CAST(COALESCE(SUM(distanceKm), 0) AS REAL) FROM cardio_entries")
    fun observeTotalCardioDistanceKm(): Flow<Float>

    @Query("SELECT COALESCE(MAX(weightKg), 0) FROM strength_sets")
    fun observeBestStrengthWeightKg(): Flow<Float>

    @Query("SELECT dateEpochDay, weightKg, bodyFatPercentage, waistCm, sleepHours, fatigueScore, notes FROM body_metrics ORDER BY dateEpochDay ASC")
    fun observeBodyMetricTrend(): Flow<List<BodyMetricTrend>>

    @Query("SELECT dateEpochDay FROM workout_logs ORDER BY dateEpochDay ASC")
    fun observeWorkoutDates(): Flow<List<Long>>

    @Query("""
        SELECT w.dateEpochDay, c.activityType, c.durationMinutes, c.distanceKm
        FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id
        WHERE w.dateEpochDay BETWEEN :startDay AND :endDay ORDER BY w.dateEpochDay ASC
    """)
    fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<CardioByDate>>

    @Query("""
        SELECT w.dateEpochDay, SUM(c.durationMinutes) AS totalMinutes
        FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id
        WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
        GROUP BY w.dateEpochDay ORDER BY w.dateEpochDay ASC
    """)
    fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationRow>>

    @Query("""
        SELECT w.dateEpochDay, e.name AS exerciseName, MAX(s.weightKg) AS maxWeight,
               SUM(s.weightKg * s.reps) AS volume
        FROM strength_sets s JOIN strength_exercises e ON s.exerciseId = e.id
        JOIN workout_logs w ON e.workoutLogId = w.id
        WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
        GROUP BY w.dateEpochDay, e.name ORDER BY w.dateEpochDay ASC
    """)
    fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<List<StrengthTrendRow>>

    @Query("""
        SELECT COALESCE(SUM(c.durationMinutes), 0)
        FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id
        WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
    """)
    fun observeCardioMinutesBetween(startDay: Long, endDay: Long): Flow<Int>
}

data class BodyMetricTrend(
    val dateEpochDay: Long,
    val weightKg: Float,
    val bodyFatPercentage: Float?,
    val waistCm: Float?,
    val sleepHours: Float?,
    val fatigueScore: Int?,
    val notes: String,
)

data class CardioByDate(
    val dateEpochDay: Long,
    val activityType: String,
    val durationMinutes: Int,
    val distanceKm: Float,
)

data class CardioDurationRow(
    val dateEpochDay: Long,
    val totalMinutes: Int,
)

data class StrengthTrendRow(
    val dateEpochDay: Long,
    val exerciseName: String,
    val maxWeight: Float,
    val volume: Float,
)
