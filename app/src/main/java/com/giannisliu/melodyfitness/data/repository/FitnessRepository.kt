package com.giannisliu.melodyfitness.data.repository

import kotlinx.coroutines.flow.Flow

data class StrengthSetInput(
    val weightKg: Float,
    val reps: Int,
    val notes: String = "",
)

data class StrengthExerciseInput(
    val name: String,
    val notes: String = "",
    val sets: List<StrengthSetInput>,
)

data class StrengthWorkoutInput(
    val title: String,
    val exercises: List<StrengthExerciseInput>,
    val notes: String = "",
)

data class CardioWorkoutInput(
    val activityType: String,
    val durationMinutes: Int,
    val distanceKm: Float,
    val averagePace: String,
    val notes: String,
)

data class BodyMetricInput(
    val weightKg: Float,
    val bodyFatPercentage: Float?,
    val waistCm: Float?,
    val sleepHours: Float?,
    val fatigueScore: Int?,
    val notes: String,
)

enum class GoalType(val label: String) {
    WEIGHT("体重"),
    FREQUENCY("频率"),
    PERFORMANCE("表现"),
}

data class GoalInput(
    val title: String,
    val type: GoalType,
    val startValue: Float,
    val currentValue: Float,
    val targetValue: Float,
    val unit: String,
)

data class WorkoutSummary(
    val id: Long,
    val dateText: String,
    val title: String,
    val detailText: String,
)

data class StrengthSetHistory(
    val setOrder: Int,
    val weightKg: Float,
    val reps: Int,
)

data class StrengthExerciseHistory(
    val name: String,
    val setCount: Int,
    val sets: List<StrengthSetHistory>,
)

data class StrengthExerciseTemplate(
    val name: String,
    val lastWeightKg: Float,
    val lastReps: Int,
)

data class CardioEntryHistory(
    val activityType: String,
    val durationMinutes: Int,
    val distanceKm: Float,
    val averagePace: String,
)

data class WorkoutHistoryItem(
    val id: Long,
    val dateText: String,
    val title: String,
    val detailText: String,
    val notes: String,
    val strengthExercises: List<StrengthExerciseHistory>,
    val cardioEntries: List<CardioEntryHistory>,
)

data class BodyMetricSummary(
    val dateText: String,
    val weightKg: Float,
    val bodyFatPercentage: Float?,
    val waistCm: Float?,
    val sleepHours: Float?,
    val fatigueScore: Int?,
    val notes: String,
)

data class HomeSnapshot(
    val weeklyWorkoutCount: Int,
    val latestWeightKg: Float?,
    val latestBodyMetric: BodyMetricSummary?,
    val recentWorkouts: List<WorkoutSummary>,
)

data class GoalSummary(
    val id: Long,
    val title: String,
    val typeLabel: String,
    val currentValue: Float,
    val targetValue: Float,
    val unit: String,
    val progressPercent: Int,
)

data class StatsSnapshot(
    val weeklyWorkoutCount: Int,
    val totalCardioMinutes: Int,
    val totalCardioDistanceKm: Float,
    val bestStrengthWeightKg: Float,
    val latestWeightKg: Float?,
    val weightChangeSinceLastRecordKg: Float?,
    val latestBodyFatPercentage: Float?,
    val latestWaistCm: Float?,
    val latestSleepHours: Float?,
    val latestFatigueScore: Int?,
)

enum class WeightUnit(val label: String, val symbol: String) {
    KG("公斤", "kg"),
    LB("磅", "lb"),
}

data class BodyMetricTrendPoint(
    val dateEpochDay: Long,
    val weightKg: Float,
    val bodyFatPercentage: Float?,
    val waistCm: Float?,
    val sleepHours: Float?,
    val fatigueScore: Int?,
)

data class WeekOverWeekChanges(
    val workoutCountChange: Int? = null,
    val previousWeekWorkoutCount: Int = 0,
    val cardioMinutesChange: Int? = null,
    val previousWeekCardioMinutes: Int = 0,
    val trainingDaysChange: Int? = null,
    val previousWeekTrainingDays: Int = 0,
    val weightChange: Float? = null,
)

data class WorkoutTypeCount(
    val type: String,
    val count: Int,
)

data class CardioDurationPoint(
    val dateEpochDay: Long,
    val totalMinutes: Int,
)

data class StrengthTrendPoint(
    val dateEpochDay: Long,
    val maxWeightKg: Float,
    val volumeKg: Float,
)

data class WeeklyWorkoutCount(
    val weekLabel: String,
    val count: Int,
)

data class SparklineData(
    val weeklyWorkoutCounts: List<Int> = emptyList(),
    val weeklyCardioMinutes: List<Int> = emptyList(),
    val weeklyTrainingDays: List<Int> = emptyList(),
    val weeklyWeights: List<Float> = emptyList(),
)

interface FitnessRepository {
    fun observeHomeSnapshot(): Flow<HomeSnapshot>
    fun observeWorkoutHistory(): Flow<List<WorkoutHistoryItem>>
    fun observeStrengthTemplates(): Flow<List<StrengthExerciseTemplate>>
    fun observeGoals(): Flow<List<GoalSummary>>
    fun observeStatsSnapshot(): Flow<StatsSnapshot>
    fun observeBodyMetricTrend(): Flow<List<BodyMetricTrendPoint>>
    fun observeWeeklyWorkoutCounts(weeks: Int): Flow<List<WeeklyWorkoutCount>>
    fun observeSparklineData(): Flow<SparklineData>
    fun observeWeekOverWeekChanges(): Flow<WeekOverWeekChanges>
    fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<WorkoutTypeCount>>
    fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationPoint>>
    fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<Map<String, List<StrengthTrendPoint>>>

    suspend fun saveStrengthWorkout(input: StrengthWorkoutInput)
    suspend fun saveCardioWorkout(input: CardioWorkoutInput)
    suspend fun saveBodyMetric(input: BodyMetricInput)
    suspend fun saveGoal(input: GoalInput)
    suspend fun exportBackupJson(weightUnit: WeightUnit): String
    suspend fun updateWorkoutHistoryItem(
        workoutId: Long,
        title: String,
        notes: String,
    )
    suspend fun deleteWorkoutHistoryItem(workoutId: Long)
}
