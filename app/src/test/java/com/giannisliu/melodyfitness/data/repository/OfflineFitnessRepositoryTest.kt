package com.giannisliu.melodyfitness.data.repository

import com.giannisliu.melodyfitness.data.local.dao.BodyMetricDao
import com.giannisliu.melodyfitness.data.local.dao.GoalDao
import com.giannisliu.melodyfitness.data.local.dao.StatsDao
import com.giannisliu.melodyfitness.data.local.dao.WorkoutLogDao
import com.giannisliu.melodyfitness.data.local.entity.BodyMetricEntity
import com.giannisliu.melodyfitness.data.local.entity.CardioEntryEntity
import com.giannisliu.melodyfitness.data.local.entity.GoalEntity
import com.giannisliu.melodyfitness.data.local.entity.StrengthExerciseEntity
import com.giannisliu.melodyfitness.data.local.entity.StrengthExerciseWithSets
import com.giannisliu.melodyfitness.data.local.entity.StrengthSetEntity
import com.giannisliu.melodyfitness.data.local.entity.WorkoutLogEntity
import com.giannisliu.melodyfitness.data.local.entity.WorkoutWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineFitnessRepositoryTest {

    @Test
    fun delete_workout_history_item_removes_workout_from_observed_history() = runBlocking {
        val workoutDao = FakeWorkoutLogDao(
            initialWorkouts = listOf(
                fakeWorkout(id = 1L, title = "胸肩训练", notes = "训练状态不错"),
                fakeWorkout(id = 2L, title = "腿部训练", notes = "最后一组很累"),
            ),
        )
        val repository = createRepository(workoutDao = workoutDao)

        repository.deleteWorkoutHistoryItem(workoutId = 1L)

        val history = repository.observeWorkoutHistory().first()
        assertEquals(listOf(2L), history.map { it.id })
    }

    @Test
    fun update_workout_history_item_updates_observed_title_and_notes() = runBlocking {
        val workoutDao = FakeWorkoutLogDao(
            initialWorkouts = listOf(
                fakeWorkout(id = 1L, title = "胸肩训练", notes = "旧备注"),
            ),
        )
        val repository = createRepository(workoutDao = workoutDao)

        repository.updateWorkoutHistoryItem(
            workoutId = 1L,
            title = "推训练",
            notes = "新备注",
        )

        val historyItem = repository.observeWorkoutHistory().first().single()
        assertEquals("推训练", historyItem.title)
        assertEquals("新备注", historyItem.notes)
    }

    @Test
    fun observe_strength_templates_returns_recent_unique_exercises_with_latest_set_data() = runBlocking {
        val workoutDao = FakeWorkoutLogDao(
            initialWorkouts = listOf(
                fakeWorkout(
                    id = 1L,
                    title = "推训练",
                    notes = "",
                    exercises = listOf(
                        fakeExercise(
                            exerciseId = 11L,
                            workoutLogId = 1L,
                            name = "卧推",
                            sets = listOf(60f to 8, 62.5f to 6),
                        ),
                        fakeExercise(
                            exerciseId = 12L,
                            workoutLogId = 1L,
                            name = "上斜卧推",
                            sets = listOf(22.5f to 10),
                        ),
                    ),
                ),
                fakeWorkout(
                    id = 2L,
                    title = "旧推训练",
                    notes = "",
                    exercises = listOf(
                        fakeExercise(
                            exerciseId = 21L,
                            workoutLogId = 2L,
                            name = "卧推",
                            sets = listOf(55f to 10),
                        ),
                    ),
                ),
            ),
        )
        val repository = createRepository(workoutDao = workoutDao)

        val templates = repository.observeStrengthTemplates().first()

        assertEquals(listOf("卧推", "上斜卧推"), templates.map { it.name })
        assertEquals(62.5f, templates.first().lastWeightKg)
        assertEquals(6, templates.first().lastReps)
    }

    @Test
    fun observe_stats_snapshot_includes_latest_recovery_fields_and_weight_delta() = runBlocking {
        val bodyMetricDao = FakeBodyMetricDao(
            initialMetrics = listOf(
                BodyMetricEntity(
                    id = 2L,
                    dateEpochDay = 20_209L,
                    weightKg = 77.2f,
                    bodyFatPercentage = 17.5f,
                    waistCm = 83f,
                    sleepHours = 6.5f,
                    fatigueScore = 7,
                    notes = "恢复一般",
                ),
                BodyMetricEntity(
                    id = 1L,
                    dateEpochDay = 20_208L,
                    weightKg = 78f,
                    bodyFatPercentage = 18f,
                    waistCm = 84f,
                    sleepHours = 7.2f,
                    fatigueScore = 5,
                    notes = "恢复不错",
                ),
            ),
        )
        val repository = OfflineFitnessRepository(
            workoutLogDao = FakeWorkoutLogDao(),
            bodyMetricDao = bodyMetricDao,
            goalDao = FakeGoalDao(),
            statsDao = FakeStatsDao(),
        )

        val stats = repository.observeStatsSnapshot().first()

        assertEquals(77.2f, stats.latestWeightKg!!, 0.001f)
        assertEquals(-0.8f, stats.weightChangeSinceLastRecordKg!!, 0.001f)
        assertEquals(17.5f, stats.latestBodyFatPercentage!!, 0.001f)
        assertEquals(83f, stats.latestWaistCm!!, 0.001f)
        assertEquals(6.5f, stats.latestSleepHours!!, 0.001f)
        assertEquals(7, stats.latestFatigueScore)
    }

    @Test
    fun save_body_metric_persists_extended_fields() = runBlocking {
        val bodyMetricDao = FakeBodyMetricDao()
        val repository = OfflineFitnessRepository(
            workoutLogDao = FakeWorkoutLogDao(),
            bodyMetricDao = bodyMetricDao,
            goalDao = FakeGoalDao(),
            statsDao = FakeStatsDao(),
        )

        repository.saveBodyMetric(
            BodyMetricInput(
                weightKg = 76.8f,
                bodyFatPercentage = 16.2f,
                waistCm = 82f,
                sleepHours = 7f,
                fatigueScore = 3,
                notes = "状态轻松",
            ),
        )

        val inserted = bodyMetricDao.observeLatestMetric().first()
        requireNotNull(inserted)
        assertEquals(76.8f, inserted.weightKg, 0.001f)
        assertEquals(16.2f, inserted.bodyFatPercentage!!, 0.001f)
        assertEquals(82f, inserted.waistCm!!, 0.001f)
        assertEquals(7f, inserted.sleepHours!!, 0.001f)
        assertEquals(3, inserted.fatigueScore)
    }

    private fun createRepository(
        workoutDao: FakeWorkoutLogDao = FakeWorkoutLogDao(),
    ): OfflineFitnessRepository {
        return OfflineFitnessRepository(
            workoutLogDao = workoutDao,
            bodyMetricDao = FakeBodyMetricDao(),
            goalDao = FakeGoalDao(),
            statsDao = FakeStatsDao(),
        )
    }

    private fun fakeWorkout(
        id: Long,
        title: String,
        notes: String,
        exercises: List<StrengthExerciseWithSets> = listOf(
            fakeExercise(
                exerciseId = id * 10,
                workoutLogId = id,
                name = "卧推",
                sets = listOf(60f to 8),
            ),
        ),
    ): WorkoutWithDetails {
        return WorkoutWithDetails(
            workoutLog = WorkoutLogEntity(
                id = id,
                dateEpochDay = 20_208L,
                title = title,
                notes = notes,
                createdAtMillis = id,
            ),
            strengthExercises = exercises,
            cardioEntries = emptyList(),
        )
    }

    private fun fakeExercise(
        exerciseId: Long,
        workoutLogId: Long,
        name: String,
        sets: List<Pair<Float, Int>>,
    ): StrengthExerciseWithSets {
        return StrengthExerciseWithSets(
            exercise = StrengthExerciseEntity(
                id = exerciseId,
                workoutLogId = workoutLogId,
                name = name,
                notes = "",
                displayOrder = 0,
            ),
            sets = sets.mapIndexed { index, (weight, reps) ->
                StrengthSetEntity(
                    id = exerciseId * 100 + index,
                    exerciseId = exerciseId,
                    setOrder = index + 1,
                    weightKg = weight,
                    reps = reps,
                    notes = "",
                )
            },
        )
    }
}

private class FakeWorkoutLogDao(
    initialWorkouts: List<WorkoutWithDetails> = emptyList(),
) : WorkoutLogDao {
    private val workouts = MutableStateFlow(initialWorkouts)

    override fun observeWorkoutLogs(): Flow<List<WorkoutWithDetails>> = workouts

    override fun observeWorkoutCountSince(startEpochDay: Long): Flow<Int> {
        return MutableStateFlow(workouts.value.count { it.workoutLog.dateEpochDay >= startEpochDay })
    }

    override suspend fun insertWorkoutLog(workoutLog: WorkoutLogEntity): Long = workoutLog.id

    override suspend fun insertStrengthExercise(exercise: StrengthExerciseEntity): Long = exercise.id

    override suspend fun insertStrengthSets(sets: List<StrengthSetEntity>) = Unit

    override suspend fun insertCardioEntry(cardioEntry: CardioEntryEntity) = Unit

    override suspend fun updateWorkoutLogMetadata(
        workoutId: Long,
        title: String,
        notes: String,
    ) {
        workouts.value = workouts.value.map { workout ->
            if (workout.workoutLog.id == workoutId) {
                workout.copy(
                    workoutLog = workout.workoutLog.copy(
                        title = title,
                        notes = notes,
                    ),
                )
            } else {
                workout
            }
        }
    }

    override suspend fun deleteWorkoutLogById(workoutId: Long) {
        workouts.value = workouts.value.filterNot { it.workoutLog.id == workoutId }
    }
}

private class FakeBodyMetricDao(
    initialMetrics: List<BodyMetricEntity> = emptyList(),
) : BodyMetricDao {
    private val metrics = MutableStateFlow(initialMetrics.sortedByDescending { it.id })

    override fun observeLatestMetric(): Flow<BodyMetricEntity?> {
        return metrics.map { it.firstOrNull() }
    }

    override fun observeRecentMetrics(limit: Int): Flow<List<BodyMetricEntity>> {
        return metrics.map { it.take(limit) }
    }

    override suspend fun insertMetric(metric: BodyMetricEntity) {
        val nextId = (metrics.value.maxOfOrNull { it.id } ?: 0L) + 1L
        metrics.value = listOf(metric.copy(id = nextId)) + metrics.value
    }
}

private class FakeGoalDao : GoalDao {
    override fun observeGoals(): Flow<List<GoalEntity>> = MutableStateFlow(emptyList())
    override suspend fun insertGoal(goal: GoalEntity) = Unit
}

private class FakeStatsDao : StatsDao {
    override fun observeTotalCardioMinutes(): Flow<Int> = MutableStateFlow(0)
    override fun observeTotalCardioDistanceKm(): Flow<Float> = MutableStateFlow(0f)
    override fun observeBestStrengthWeightKg(): Flow<Float> = MutableStateFlow(0f)
}
