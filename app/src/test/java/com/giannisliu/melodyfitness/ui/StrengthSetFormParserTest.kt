package com.giannisliu.melodyfitness.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthSetFormParserTest {

    @Test
    fun parses_bodyweight_set_without_weight_as_zero_kg() {
        val input = parseStrengthSetInput(
            StrengthSetFormState(
                weightText = "",
                repsText = "12",
                isBodyweight = true,
            ),
        )

        requireNotNull(input)
        assertEquals(0f, input.weightKg, 0.001f)
        assertEquals(12, input.reps)
    }

    @Test
    fun requires_weight_for_non_bodyweight_set() {
        val input = parseStrengthSetInput(
            StrengthSetFormState(
                weightText = "",
                repsText = "8",
                isBodyweight = false,
            ),
        )

        assertNull(input)
    }

    @Test
    fun rejects_invalid_optional_weight_for_bodyweight_set() {
        val input = parseStrengthSetInput(
            StrengthSetFormState(
                weightText = "abc",
                repsText = "10",
                isBodyweight = true,
            ),
        )

        assertNull(input)
    }

    @Test
    fun recognizes_bodyweight_and_jump_presets() {
        assertTrue(isBodyweightExerciseName("俯卧撑"))
        assertTrue(isBodyweightExerciseName("助跑摸高"))
    }
}
