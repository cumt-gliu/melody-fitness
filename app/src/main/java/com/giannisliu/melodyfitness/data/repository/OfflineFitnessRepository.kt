package com.giannisliu.melodyfitness.data.repository

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
import com.giannisliu.melodyfitness.domain.BackupJsonFormatter
import com.giannisliu.melodyfitness.domain.GoalProgressCalculator
import com.giannisliu.melodyfitness.domain.WorkoutHistoryFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineFitnessRepository(
    private val workoutLogDao: WorkoutLogDao,
    private val bodyMetricDao: BodyMetricDao,
    private val goalDao: GoalDao,
    private val statsDao: StatsDao,
) : FitnessRepository {
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")

    override fun observeHomeSnapshot(): Flow<HomeSnapshot> {
        return combine(
            observeWorkoutHistory(),
            workoutLogDao.observeWorkoutCountSince(startOfWeekEpochDay()),
            bodyMetricDao.observeLatestMetric(),
        ) { history, weeklyCount, latestMetric ->
            HomeSnapshot(
                weeklyWorkoutCount = weeklyCount,
                latestWeightKg = latestMetric?.weightKg,
                latestBodyMetric = latestMetric?.toSummary(),
                recentWorkouts = history.take(5).map { workout ->
                    WorkoutSummary(
                        id = workout.id,
                        dateText = workout.dateText,
                        title = workout.title,
                        detailText = workout.detailText,
                    )
                },
            )
        }
    }

    override fun observeWorkoutHistory(): Flow<List<WorkoutHistoryItem>> {
        return workoutLogDao.observeWorkoutLogs().map { workouts ->
            workouts.map { workout ->
                val date = LocalDate.ofEpochDay(workout.workoutLog.dateEpochDay)
                val strengthExercises = workout.strengthExercises.map { exercise ->
                    StrengthExerciseHistory(
                        name = exercise.exercise.name,
                        setCount = exercise.sets.size,
                        sets = exercise.sets
                            .sortedBy { it.setOrder }
                            .map { set ->
                                StrengthSetHistory(
                                    setOrder = set.setOrder,
                                    weightKg = set.weightKg,
                                    reps = set.reps,
                                )
                            },
                    )
                }
                val cardioEntries = workout.cardioEntries.map { cardio ->
                    CardioEntryHistory(
                        activityType = cardio.activityType,
                        durationMinutes = cardio.durationMinutes,
                        distanceKm = cardio.distanceKm,
                        averagePace = cardio.averagePace,
                    )
                }
                WorkoutHistoryItem(
                    id = workout.workoutLog.id,
                    dateText = date.format(dateFormatter),
                    title = workout.workoutLog.title,
                    detailText = WorkoutHistoryFormatter.buildDetailText(
                        exerciseCount = strengthExercises.size,
                        setCount = strengthExercises.sumOf { it.sets.size },
                        cardioCount = cardioEntries.size,
                    ),
                    notes = workout.workoutLog.notes,
                    strengthExercises = strengthExercises,
                    cardioEntries = cardioEntries,
                )
            }
        }
    }

    override fun observeStrengthTemplates(): Flow<List<StrengthExerciseTemplate>> {
        return observeWorkoutHistory().map { history ->
            val templates = linkedMapOf<String, StrengthExerciseTemplate>()
            history.forEach { workout ->
                workout.strengthExercises.forEach { exercise ->
                    val lastSet = exercise.sets.maxByOrNull { it.setOrder } ?: return@forEach
                    templates.putIfAbsent(
                        exercise.name,
                        StrengthExerciseTemplate(
                            name = exercise.name,
                            lastWeightKg = lastSet.weightKg,
                            lastReps = lastSet.reps,
                        ),
                    )
                }
            }
            templates.values.toList()
        }
    }

    override fun observeGoals(): Flow<List<GoalSummary>> {
        return combine(
            goalDao.observeGoals(),
            bodyMetricDao.observeLatestMetric(),
            workoutLogDao.observeWorkoutCountSince(startOfWeekEpochDay()),
        ) { goals, latestMetric, weeklyWorkoutCount ->
            goals.map { goal ->
                val liveCurrentValue = when (goal.type) {
                    GoalType.WEIGHT.name -> latestMetric?.weightKg ?: goal.currentValue
                    GoalType.FREQUENCY.name -> weeklyWorkoutCount.toFloat()
                    else -> goal.currentValue
                }
                GoalSummary(
                    id = goal.id,
                    title = goal.title,
                    typeLabel = goal.type.toGoalType().label,
                    currentValue = liveCurrentValue,
                    targetValue = goal.targetValue,
                    unit = goal.unit,
                    progressPercent = GoalProgressCalculator.calculate(
                        startValue = goal.startValue,
                        currentValue = liveCurrentValue,
                        targetValue = goal.targetValue,
                    ),
                )
            }
        }
    }

    override fun observeStatsSnapshot(): Flow<StatsSnapshot> {
        return combine(
            workoutLogDao.observeWorkoutCountSince(startOfWeekEpochDay()),
            statsDao.observeTotalCardioMinutes(),
            statsDao.observeTotalCardioDistanceKm(),
            statsDao.observeBestStrengthWeightKg(),
            bodyMetricDao.observeRecentMetrics(limit = 2),
        ) { weeklyCount, cardioMinutes, cardioDistance, bestStrength, recentMetrics ->
            val latestMetric = recentMetrics.firstOrNull()
            val previousMetric = recentMetrics.getOrNull(1)
            StatsSnapshot(
                weeklyWorkoutCount = weeklyCount,
                totalCardioMinutes = cardioMinutes,
                totalCardioDistanceKm = cardioDistance,
                bestStrengthWeightKg = bestStrength,
                latestWeightKg = latestMetric?.weightKg,
                weightChangeSinceLastRecordKg = if (latestMetric != null && previousMetric != null) {
                    latestMetric.weightKg - previousMetric.weightKg
                } else {
                    null
                },
                latestBodyFatPercentage = latestMetric?.bodyFatPercentage,
                latestWaistCm = latestMetric?.waistCm,
                latestSleepHours = latestMetric?.sleepHours,
                latestFatigueScore = latestMetric?.fatigueScore,
            )
        }
    }

    override suspend fun saveStrengthWorkout(input: StrengthWorkoutInput) {
        val now = System.currentTimeMillis()
        val workoutLogId = workoutLogDao.insertWorkoutLog(
            WorkoutLogEntity(
                dateEpochDay = LocalDate.now().toEpochDay(),
                title = input.title.ifBlank { "${input.exercises.firstOrNull()?.name ?: "力量"} 训练" },
                notes = input.notes,
                createdAtMillis = now,
            ),
        )
        input.exercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = workoutLogDao.insertStrengthExercise(
                StrengthExerciseEntity(
                    workoutLogId = workoutLogId,
                    name = exercise.name,
                    notes = exercise.notes,
                    displayOrder = exerciseIndex,
                ),
            )
            workoutLogDao.insertStrengthSets(
                exercise.sets.mapIndexed { setIndex, set ->
                    StrengthSetEntity(
                        exerciseId = exerciseId,
                        setOrder = setIndex + 1,
                        weightKg = set.weightKg,
                        reps = set.reps,
                        notes = set.notes,
                    )
                },
            )
        }
    }

    override suspend fun saveCardioWorkout(input: CardioWorkoutInput) {
        val workoutLogId = workoutLogDao.insertWorkoutLog(
            WorkoutLogEntity(
                dateEpochDay = LocalDate.now().toEpochDay(),
                title = "${input.activityType} 有氧",
                notes = input.notes,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )

        workoutLogDao.insertCardioEntry(
            CardioEntryEntity(
                workoutLogId = workoutLogId,
                activityType = input.activityType,
                durationMinutes = input.durationMinutes,
                distanceKm = input.distanceKm,
                averagePace = input.averagePace,
                notes = input.notes,
            ),
        )
    }

    override suspend fun saveBodyMetric(input: BodyMetricInput) {
        bodyMetricDao.insertMetric(
            BodyMetricEntity(
                dateEpochDay = LocalDate.now().toEpochDay(),
                weightKg = input.weightKg,
                bodyFatPercentage = input.bodyFatPercentage,
                waistCm = input.waistCm,
                sleepHours = input.sleepHours,
                fatigueScore = input.fatigueScore,
                notes = input.notes,
            ),
        )
    }

    override suspend fun saveGoal(input: GoalInput) {
        goalDao.insertGoal(
            GoalEntity(
                title = input.title,
                type = input.type.name,
                startValue = input.startValue,
                currentValue = input.currentValue,
                targetValue = input.targetValue,
                unit = input.unit,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun exportBackupJson(weightUnit: WeightUnit): String {
        return BackupJsonFormatter.format(
            exportedAt = OffsetDateTime.now().toString(),
            weightUnit = weightUnit,
            history = observeWorkoutHistory().first(),
            goals = observeGoals().first(),
            stats = observeStatsSnapshot().first(),
        )
    }

    override suspend fun updateWorkoutHistoryItem(
        workoutId: Long,
        title: String,
        notes: String,
    ) {
        workoutLogDao.updateWorkoutLogMetadata(
            workoutId = workoutId,
            title = title,
            notes = notes,
        )
    }

    override suspend fun deleteWorkoutHistoryItem(workoutId: Long) {
        workoutLogDao.deleteWorkoutLogById(workoutId)
    }

    private fun startOfWeekEpochDay(today: LocalDate = LocalDate.now()): Long {
        val delta = (today.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        return today.minusDays(delta.toLong()).toEpochDay()
    }

    private fun String.toGoalType(): GoalType {
        return GoalType.entries.firstOrNull { it.name == this } ?: GoalType.PERFORMANCE
    }

    private fun BodyMetricEntity.toSummary(): BodyMetricSummary {
        return BodyMetricSummary(
            dateText = LocalDate.ofEpochDay(dateEpochDay).format(dateFormatter),
            weightKg = weightKg,
            bodyFatPercentage = bodyFatPercentage,
            waistCm = waistCm,
            sleepHours = sleepHours,
            fatigueScore = fatigueScore,
            notes = notes,
        )
    }
}
