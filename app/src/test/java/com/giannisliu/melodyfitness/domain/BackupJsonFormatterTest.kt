package com.giannisliu.melodyfitness.domain

import com.giannisliu.melodyfitness.data.repository.GoalSummary
import com.giannisliu.melodyfitness.data.repository.StatsSnapshot
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseHistory
import com.giannisliu.melodyfitness.data.repository.StrengthSetHistory
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import com.giannisliu.melodyfitness.data.repository.WorkoutHistoryItem
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonFormatterTest {

    @Test
    fun formats_backup_json_with_settings_history_goals_and_stats() {
        val json = BackupJsonFormatter.format(
            exportedAt = "2026-04-30T10:00:00+08:00",
            weightUnit = WeightUnit.KG,
            history = listOf(
                WorkoutHistoryItem(
                    id = 1L,
                    dateText = "4月30日",
                    title = "推训练",
                    detailText = "2 个动作 · 5 组",
                    notes = "状态不错",
                    strengthExercises = listOf(
                        StrengthExerciseHistory(
                            name = "卧推",
                            setCount = 2,
                            sets = listOf(
                                StrengthSetHistory(setOrder = 1, weightKg = 60f, reps = 8),
                            ),
                        ),
                    ),
                    cardioEntries = emptyList(),
                ),
            ),
            goals = listOf(
                GoalSummary(
                    id = 1L,
                    title = "体重降到 72kg",
                    typeLabel = "体重",
                    currentValue = 78f,
                    targetValue = 72f,
                    unit = "kg",
                    progressPercent = 50,
                ),
            ),
            stats = StatsSnapshot(
                weeklyWorkoutCount = 4,
                totalCardioMinutes = 80,
                totalCardioDistanceKm = 12f,
                bestStrengthWeightKg = 100f,
                latestWeightKg = 78f,
                weightChangeSinceLastRecordKg = -0.8f,
                latestBodyFatPercentage = 18.5f,
                latestWaistCm = 84f,
                latestSleepHours = 7.5f,
                latestFatigueScore = 4,
            ),
        )

        assertTrue(json.contains("\"weightUnit\":\"KG\""))
        assertTrue(json.contains("\"title\":\"推训练\""))
        assertTrue(json.contains("\"name\":\"卧推\""))
        assertTrue(json.contains("\"title\":\"体重降到 72kg\""))
        assertTrue(json.contains("\"weeklyWorkoutCount\":4"))
        assertTrue(json.contains("\"latestSleepHours\":7.5"))
        assertTrue(json.contains("\"latestFatigueScore\":4"))
    }
}
