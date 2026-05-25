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
import java.time.temporal.WeekFields
import java.util.Locale
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

    override fun observeBodyMetricTrend(): Flow<List<BodyMetricTrendPoint>> {
        return statsDao.observeBodyMetricTrend().map { rows ->
            rows.map { row ->
                BodyMetricTrendPoint(
                    dateEpochDay = row.dateEpochDay,
                    weightKg = row.weightKg,
                    bodyFatPercentage = row.bodyFatPercentage,
                    waistCm = row.waistCm,
                    sleepHours = row.sleepHours,
                    fatigueScore = row.fatigueScore,
                )
            }
        }
    }

    override fun observeWeeklyWorkoutCounts(weeks: Int): Flow<List<WeeklyWorkoutCount>> {
        return observeWeeklyTrainingComparison(weeks).map { comparisons ->
            comparisons.map { comparison ->
                WeeklyWorkoutCount(
                    weekLabel = comparison.weekLabel,
                    count = comparison.workoutCount,
                )
            }
        }
    }

    override fun observeWeeklyTrainingComparison(weeks: Int): Flow<List<WeeklyTrainingComparison>> {
        return workoutLogDao.observeWorkoutLogs().map { workouts ->
            val today = LocalDate.now()
            val weekStarts = (weeks - 1 downTo 0).map { weekAgo ->
                startOfWeek(today.minusWeeks(weekAgo.toLong()))
            }
            val weekStartEpochs = weekStarts.map { it.toEpochDay() }

            val workoutsByWeek = mutableMapOf<Long, Int>()
            val trainingDaysByWeek = mutableMapOf<Long, MutableSet<Long>>()
            val cardioMinutesByWeek = mutableMapOf<Long, Int>()

            workouts.forEach { workout ->
                val workoutDay = workout.workoutLog.dateEpochDay
                val weekStart = startOfWeek(LocalDate.ofEpochDay(workoutDay)).toEpochDay()
                if (weekStart !in weekStartEpochs) return@forEach

                workoutsByWeek[weekStart] = (workoutsByWeek[weekStart] ?: 0) + 1
                trainingDaysByWeek.getOrPut(weekStart) { mutableSetOf() }.add(workoutDay)
                cardioMinutesByWeek[weekStart] = (cardioMinutesByWeek[weekStart] ?: 0) +
                    workout.cardioEntries.sumOf { it.durationMinutes }
            }

            weekStarts.mapIndexed { index, weekStart ->
                val weekStartEpoch = weekStart.toEpochDay()
                val workoutCount = workoutsByWeek[weekStartEpoch] ?: 0
                val trainingDays = trainingDaysByWeek[weekStartEpoch]?.size ?: 0
                val cardioMinutes = cardioMinutesByWeek[weekStartEpoch] ?: 0
                val previous = index.takeIf { it > 0 }?.let { previousIndex ->
                    val previousEpoch = weekStarts[previousIndex - 1].toEpochDay()
                    Triple(
                        workoutsByWeek[previousEpoch] ?: 0,
                        trainingDaysByWeek[previousEpoch]?.size ?: 0,
                        cardioMinutesByWeek[previousEpoch] ?: 0,
                    )
                }

                WeeklyTrainingComparison(
                    weekLabel = "${weekStart.monthValue}/${weekStart.dayOfMonth}",
                    workoutCount = workoutCount,
                    trainingDays = trainingDays,
                    cardioMinutes = cardioMinutes,
                    workoutCountChange = previous?.let { workoutCount - it.first },
                    trainingDaysChange = previous?.let { trainingDays - it.second },
                    cardioMinutesChange = previous?.let { cardioMinutes - it.third },
                )
            }
        }
    }

    override fun observeSparklineData(): Flow<SparklineData> {
        val today = LocalDate.now()
        val cutoff = today.minusWeeks(8).toEpochDay()

        return combine(
            observeWeeklyWorkoutCounts(weeks = 8),
            workoutLogDao.observeWorkoutLogs(),
            statsDao.observeWorkoutDates(),
            statsDao.observeBodyMetricTrend(),
        ) { weeklyCounts, workouts, dates, bodyTrend ->
            // weekly cardio minutes from workout logs
            val cardioByWeek = mutableMapOf<Int, Int>()
            workouts.filter { it.workoutLog.dateEpochDay >= cutoff }.forEach { w ->
                if (w.cardioEntries.isNotEmpty()) {
                    val yw = LocalDate.ofEpochDay(w.workoutLog.dateEpochDay).toYearWeek()
                    val mins = w.cardioEntries.sumOf { it.durationMinutes }
                    cardioByWeek[yw] = (cardioByWeek[yw] ?: 0) + mins
                }
            }

            // weekly training days from dates
            val daysByWeek = mutableMapOf<Int, MutableSet<Long>>()
            dates.filter { it >= cutoff }.forEach { day ->
                val yw = LocalDate.ofEpochDay(day).toYearWeek()
                daysByWeek.getOrPut(yw) { mutableSetOf() }.add(day)
            }

            // weekly weights (latest per week) from body trend
            val weightByWeek = mutableMapOf<Int, Float>()
            bodyTrend.filter { it.dateEpochDay >= cutoff }.forEach { point ->
                weightByWeek[LocalDate.ofEpochDay(point.dateEpochDay).toYearWeek()] = point.weightKg
            }

            // align to 8-week window
            val weeks = (0 until 8).map { weekAgo ->
                today.minusWeeks(weekAgo.toLong()).toYearWeek()
            }.reversed()

            SparklineData(
                weeklyWorkoutCounts = weeklyCounts.map { it.count },
                weeklyCardioMinutes = weeks.map { cardioByWeek[it] ?: 0 },
                weeklyTrainingDays = weeks.map { daysByWeek[it]?.size ?: 0 },
                weeklyWeights = weeks.mapNotNull { weightByWeek[it] },
            )
        }
    }

    override fun observeWeekOverWeekChanges(): Flow<WeekOverWeekChanges> {
        val today = LocalDate.now()
        val thisWeekStart = today.with(DayOfWeek.MONDAY).toEpochDay()
        val lastWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY).toEpochDay()
        val lastWeekEnd = thisWeekStart - 1
        val todayEpochDay = today.toEpochDay()

        val thisWeek = combine(
            workoutLogDao.observeWorkoutCountSince(thisWeekStart),
            statsDao.observeCardioMinutesBetween(thisWeekStart, todayEpochDay),
            workoutLogDao.observeTrainingDaysBetween(thisWeekStart, todayEpochDay),
        ) { wc, cm, td -> Triple(wc, cm, td) }

        val lastWeek = combine(
            workoutLogDao.observeWorkoutCountBetween(lastWeekStart, lastWeekEnd),
            statsDao.observeCardioMinutesBetween(lastWeekStart, lastWeekEnd),
            workoutLogDao.observeTrainingDaysBetween(lastWeekStart, lastWeekEnd),
        ) { wc, cm, td -> Triple(wc, cm, td) }

        return combine(thisWeek, lastWeek) { tw, lw ->
            WeekOverWeekChanges(
                workoutCountChange = tw.first - lw.first,
                previousWeekWorkoutCount = lw.first,
                cardioMinutesChange = tw.second - lw.second,
                previousWeekCardioMinutes = lw.second,
                trainingDaysChange = tw.third - lw.third,
                previousWeekTrainingDays = lw.third,
            )
        }
    }

    override fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<WorkoutTypeCount>> {
        return combine(
            statsDao.observeCardioByDateRange(startDay, endDay),
            workoutLogDao.observeWorkoutLogs(),
        ) { cardioRows, workouts ->
            val filteredWorkouts = workouts.filter { w ->
                w.workoutLog.dateEpochDay in startDay..endDay
            }
            val strengthCount = filteredWorkouts.count { it.strengthExercises.isNotEmpty() }
            val cardioTypeCounts = cardioRows
                .groupBy { it.activityType }
                .mapValues { it.value.size }

            val list = mutableListOf<WorkoutTypeCount>()
            if (strengthCount > 0) list.add(WorkoutTypeCount("力量", strengthCount))
            cardioTypeCounts.forEach { (type, count) ->
                list.add(WorkoutTypeCount(type, count))
            }
            list
        }
    }

    override fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationPoint>> {
        return statsDao.observeCardioDurationTrend(startDay, endDay).map { rows ->
            rows.map { CardioDurationPoint(it.dateEpochDay, it.totalMinutes) }
        }
    }

    override fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<Map<String, List<StrengthTrendPoint>>> {
        return statsDao.observeStrengthTrend(startDay, endDay).map { rows ->
            rows.groupBy { it.exerciseName }.mapValues { entry ->
                entry.value.map {
                    StrengthTrendPoint(
                        dateEpochDay = it.dateEpochDay,
                        maxWeightKg = it.maxWeight,
                        volumeKg = it.volume,
                    )
                }
            }
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

    override suspend fun updateWorkoutHistoryDetails(
        workoutId: Long,
        input: WorkoutHistoryEditInput,
    ) {
        workoutLogDao.updateWorkoutLogMetadata(
            workoutId = workoutId,
            title = input.title,
            notes = input.notes,
        )
        workoutLogDao.deleteCardioEntriesForWorkout(workoutId)
        workoutLogDao.deleteStrengthExercisesForWorkout(workoutId)

        input.strengthExercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = workoutLogDao.insertStrengthExercise(
                StrengthExerciseEntity(
                    workoutLogId = workoutId,
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

        input.cardioEntries.forEach { cardio ->
            workoutLogDao.insertCardioEntry(
                CardioEntryEntity(
                    workoutLogId = workoutId,
                    activityType = cardio.activityType,
                    durationMinutes = cardio.durationMinutes,
                    distanceKm = cardio.distanceKm,
                    averagePace = cardio.averagePace,
                    notes = cardio.notes,
                ),
            )
        }
    }

    override suspend fun duplicateWorkoutHistoryItem(workoutId: Long) {
        val source = workoutLogDao.observeWorkoutLogs().first()
            .firstOrNull { it.workoutLog.id == workoutId }
            ?: return

        val now = System.currentTimeMillis()
        val copiedWorkoutId = workoutLogDao.insertWorkoutLog(
            WorkoutLogEntity(
                dateEpochDay = LocalDate.now().toEpochDay(),
                title = source.workoutLog.title,
                notes = source.workoutLog.notes,
                createdAtMillis = now,
            ),
        )

        source.strengthExercises.sortedBy { it.exercise.displayOrder }.forEachIndexed { exerciseIndex, exercise ->
            val copiedExerciseId = workoutLogDao.insertStrengthExercise(
                StrengthExerciseEntity(
                    workoutLogId = copiedWorkoutId,
                    name = exercise.exercise.name,
                    notes = exercise.exercise.notes,
                    displayOrder = exerciseIndex,
                ),
            )
            workoutLogDao.insertStrengthSets(
                exercise.sets.sortedBy { it.setOrder }.mapIndexed { setIndex, set ->
                    StrengthSetEntity(
                        exerciseId = copiedExerciseId,
                        setOrder = setIndex + 1,
                        weightKg = set.weightKg,
                        reps = set.reps,
                        notes = set.notes,
                    )
                },
            )
        }

        source.cardioEntries.forEach { cardio ->
            workoutLogDao.insertCardioEntry(
                CardioEntryEntity(
                    workoutLogId = copiedWorkoutId,
                    activityType = cardio.activityType,
                    durationMinutes = cardio.durationMinutes,
                    distanceKm = cardio.distanceKm,
                    averagePace = cardio.averagePace,
                    notes = cardio.notes,
                ),
            )
        }
    }

    override suspend fun deleteWorkoutHistoryItem(workoutId: Long) {
        workoutLogDao.deleteWorkoutLogById(workoutId)
    }

    private fun LocalDate.toYearWeek(weekField: java.time.temporal.TemporalField = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()): Int =
        year * 100 + get(weekField)

    private fun startOfWeek(date: LocalDate): LocalDate {
        val delta = (date.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        return date.minusDays(delta.toLong())
    }

    private fun startOfWeekEpochDay(today: LocalDate = LocalDate.now()): Long {
        return startOfWeek(today).toEpochDay()
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
