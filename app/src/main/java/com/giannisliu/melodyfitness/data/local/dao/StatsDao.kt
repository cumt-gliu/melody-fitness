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
}
