package com.giannisliu.melodyfitness.ui

import com.giannisliu.melodyfitness.data.repository.StrengthSetInput
import com.giannisliu.melodyfitness.domain.WorkoutCatalog

internal data class StrengthSetFormState(
    val weightText: String,
    val repsText: String,
    val isBodyweight: Boolean,
)

internal fun isBodyweightExerciseName(name: String): Boolean {
    val trimmedName = name.trim()
    return trimmedName in WorkoutCatalog.bodyweightExercisePresets ||
        trimmedName in WorkoutCatalog.dunkExercisePresets
}

internal fun parseStrengthSetInput(state: StrengthSetFormState): StrengthSetInput? {
    val reps = state.repsText.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val weight = if (state.isBodyweight) {
        if (state.weightText.isBlank()) 0f else state.weightText.toFloatOrNull() ?: return null
    } else {
        state.weightText.toFloatOrNull() ?: return null
    }
    return StrengthSetInput(weightKg = weight, reps = reps)
}
