package com.giannisliu.melodyfitness.domain

import com.giannisliu.melodyfitness.data.repository.StrengthExerciseInput
import com.giannisliu.melodyfitness.data.repository.StrengthSetInput
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHistoryFormatterTest {

    @Test
    fun counts_total_sets_across_multiple_exercises() {
        val totalSets = WorkoutHistoryFormatter.totalSetCount(
            exercises = listOf(
                StrengthExerciseInput(
                    name = "卧推",
                    sets = listOf(
                        StrengthSetInput(weightKg = 60f, reps = 8),
                        StrengthSetInput(weightKg = 60f, reps = 7),
                    ),
                ),
                StrengthExerciseInput(
                    name = "上斜卧推",
                    sets = listOf(
                        StrengthSetInput(weightKg = 22.5f, reps = 10),
                        StrengthSetInput(weightKg = 22.5f, reps = 10),
                        StrengthSetInput(weightKg = 20f, reps = 12),
                    ),
                ),
            ),
        )

        assertEquals(5, totalSets)
    }

    @Test
    fun builds_detail_text_with_cardio_suffix_only_when_needed() {
        assertEquals(
            "2 个动作 · 5 组 · 1 条有氧",
            WorkoutHistoryFormatter.buildDetailText(
                exerciseCount = 2,
                setCount = 5,
                cardioCount = 1,
            ),
        )

        assertEquals(
            "2 个动作 · 5 组",
            WorkoutHistoryFormatter.buildDetailText(
                exerciseCount = 2,
                setCount = 5,
                cardioCount = 0,
            ),
        )
    }
}
