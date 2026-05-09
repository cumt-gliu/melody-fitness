package com.giannisliu.melodyfitness.domain

import kotlin.math.roundToInt

object GoalProgressCalculator {
    fun calculate(
        startValue: Float,
        currentValue: Float,
        targetValue: Float,
    ): Int {
        val totalDelta = targetValue - startValue
        if (totalDelta == 0f) {
            return if (currentValue == targetValue) 100 else 0
        }

        val ratio = ((currentValue - startValue) / totalDelta).coerceIn(0f, 1f)
        return (ratio * 100).roundToInt()
    }
}
