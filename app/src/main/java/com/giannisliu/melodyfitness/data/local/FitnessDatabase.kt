package com.giannisliu.melodyfitness.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.giannisliu.melodyfitness.data.local.dao.BodyMetricDao
import com.giannisliu.melodyfitness.data.local.dao.GoalDao
import com.giannisliu.melodyfitness.data.local.dao.StatsDao
import com.giannisliu.melodyfitness.data.local.dao.WorkoutLogDao
import com.giannisliu.melodyfitness.data.local.entity.BodyMetricEntity
import com.giannisliu.melodyfitness.data.local.entity.CardioEntryEntity
import com.giannisliu.melodyfitness.data.local.entity.GoalEntity
import com.giannisliu.melodyfitness.data.local.entity.StrengthExerciseEntity
import com.giannisliu.melodyfitness.data.local.entity.StrengthSetEntity
import com.giannisliu.melodyfitness.data.local.entity.WorkoutLogEntity

@Database(
    entities = [
        WorkoutLogEntity::class,
        StrengthExerciseEntity::class,
        StrengthSetEntity::class,
        CardioEntryEntity::class,
        BodyMetricEntity::class,
        GoalEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun goalDao(): GoalDao
    abstract fun statsDao(): StatsDao
}
