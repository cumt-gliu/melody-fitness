package com.giannisliu.melodyfitness.domain

import com.giannisliu.melodyfitness.data.repository.StrengthExerciseInput

object WorkoutHistoryFormatter {
    fun totalSetCount(exercises: List<StrengthExerciseInput>): Int {
        return exercises.sumOf { it.sets.size }
    }

    fun buildDetailText(
        exerciseCount: Int,
        setCount: Int,
        cardioCount: Int,
    ): String {
        return buildString {
            append("${exerciseCount} 个动作")
            append(" · ")
            append("${setCount} 组")
            if (cardioCount > 0) {
                append(" · ")
                append("${cardioCount} 条有氧")
            }
        }
    }
}
