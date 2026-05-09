package com.giannisliu.melodyfitness.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workout_logs")
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val title: String,
    val notes: String,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "strength_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutLogId")],
)
data class StrengthExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutLogId: Long,
    val name: String,
    val notes: String,
    val displayOrder: Int,
)

@Entity(
    tableName = "strength_sets",
    foreignKeys = [
        ForeignKey(
            entity = StrengthExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class StrengthSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val setOrder: Int,
    val weightKg: Float,
    val reps: Int,
    val notes: String,
)

@Entity(
    tableName = "cardio_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutLogId")],
)
data class CardioEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutLogId: Long,
    val activityType: String,
    val durationMinutes: Int,
    val distanceKm: Float,
    val averagePace: String,
    val notes: String,
)

@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val weightKg: Float,
    val bodyFatPercentage: Float?,
    val waistCm: Float?,
    val sleepHours: Float?,
    val fatigueScore: Int?,
    val notes: String,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val startValue: Float,
    val currentValue: Float,
    val targetValue: Float,
    val unit: String,
    val createdAtMillis: Long,
)

data class StrengthExerciseWithSets(
    @Embedded val exercise: StrengthExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseId",
    )
    val sets: List<StrengthSetEntity>,
)

data class WorkoutWithDetails(
    @Embedded val workoutLog: WorkoutLogEntity,
    @Relation(
        entity = StrengthExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutLogId",
    )
    val strengthExercises: List<StrengthExerciseWithSets>,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutLogId",
    )
    val cardioEntries: List<CardioEntryEntity>,
)
