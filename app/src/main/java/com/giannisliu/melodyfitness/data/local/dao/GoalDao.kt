package com.giannisliu.melodyfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.giannisliu.melodyfitness.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAtMillis DESC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insertGoal(goal: GoalEntity)
}
