package com.giannisliu.melodyfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.giannisliu.melodyfitness.data.local.entity.BodyMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricDao {
    @Query("SELECT * FROM body_metrics ORDER BY dateEpochDay DESC, id DESC LIMIT 1")
    fun observeLatestMetric(): Flow<BodyMetricEntity?>

    @Query("SELECT * FROM body_metrics ORDER BY dateEpochDay DESC, id DESC LIMIT :limit")
    fun observeRecentMetrics(limit: Int): Flow<List<BodyMetricEntity>>

    @Insert
    suspend fun insertMetric(metric: BodyMetricEntity)
}
