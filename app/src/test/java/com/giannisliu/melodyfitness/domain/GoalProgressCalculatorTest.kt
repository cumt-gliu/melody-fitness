package com.giannisliu.melodyfitness.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressCalculatorTest {

    @Test
    fun calculates_percentage_for_weight_loss_goals() {
        val progress = GoalProgressCalculator.calculate(
            startValue = 82f,
            currentValue = 78f,
            targetValue = 74f,
        )

        assertEquals(50, progress)
    }

    @Test
    fun calculates_percentage_for_weight_gain_goals() {
        val progress = GoalProgressCalculator.calculate(
            startValue = 60f,
            currentValue = 66f,
            targetValue = 72f,
        )

        assertEquals(50, progress)
    }
}
