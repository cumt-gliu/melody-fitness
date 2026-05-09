package com.giannisliu.melodyfitness.domain

import com.giannisliu.melodyfitness.data.repository.GoalSummary
import com.giannisliu.melodyfitness.data.repository.StatsSnapshot
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import com.giannisliu.melodyfitness.data.repository.WorkoutHistoryItem

object BackupJsonFormatter {
    fun format(
        exportedAt: String,
        weightUnit: WeightUnit,
        history: List<WorkoutHistoryItem>,
        goals: List<GoalSummary>,
        stats: StatsSnapshot,
    ): String {
        return buildString {
            append("{")
            append("\"exportedAt\":").append(quote(exportedAt)).append(",")
            append("\"settings\":{")
            append("\"weightUnit\":").append(quote(weightUnit.name))
            append("},")
            append("\"history\":[")
            append(
                history.joinToString(",") { workout ->
                    buildString {
                        append("{")
                        append("\"id\":").append(workout.id).append(",")
                        append("\"dateText\":").append(quote(workout.dateText)).append(",")
                        append("\"title\":").append(quote(workout.title)).append(",")
                        append("\"detailText\":").append(quote(workout.detailText)).append(",")
                        append("\"notes\":").append(quote(workout.notes)).append(",")
                        append("\"strengthExercises\":[")
                        append(
                            workout.strengthExercises.joinToString(",") { exercise ->
                                buildString {
                                    append("{")
                                    append("\"name\":").append(quote(exercise.name)).append(",")
                                    append("\"setCount\":").append(exercise.setCount).append(",")
                                    append("\"sets\":[")
                                    append(
                                        exercise.sets.joinToString(",") { set ->
                                            "{\"setOrder\":${set.setOrder},\"weightKg\":${set.weightKg},\"reps\":${set.reps}}"
                                        },
                                    )
                                    append("]")
                                    append("}")
                                }
                            },
                        )
                        append("],")
                        append("\"cardioEntries\":[")
                        append(
                            workout.cardioEntries.joinToString(",") { cardio ->
                                buildString {
                                    append("{")
                                    append("\"activityType\":").append(quote(cardio.activityType)).append(",")
                                    append("\"durationMinutes\":").append(cardio.durationMinutes).append(",")
                                    append("\"distanceKm\":").append(cardio.distanceKm).append(",")
                                    append("\"averagePace\":").append(quote(cardio.averagePace))
                                    append("}")
                                }
                            },
                        )
                        append("]")
                        append("}")
                    }
                },
            )
            append("],")
            append("\"goals\":[")
            append(
                goals.joinToString(",") { goal ->
                    buildString {
                        append("{")
                        append("\"id\":").append(goal.id).append(",")
                        append("\"title\":").append(quote(goal.title)).append(",")
                        append("\"typeLabel\":").append(quote(goal.typeLabel)).append(",")
                        append("\"currentValue\":").append(goal.currentValue).append(",")
                        append("\"targetValue\":").append(goal.targetValue).append(",")
                        append("\"unit\":").append(quote(goal.unit)).append(",")
                        append("\"progressPercent\":").append(goal.progressPercent)
                        append("}")
                    }
                },
            )
            append("],")
            append("\"stats\":{")
            append("\"weeklyWorkoutCount\":").append(stats.weeklyWorkoutCount).append(",")
            append("\"totalCardioMinutes\":").append(stats.totalCardioMinutes).append(",")
            append("\"totalCardioDistanceKm\":").append(stats.totalCardioDistanceKm).append(",")
            append("\"bestStrengthWeightKg\":").append(stats.bestStrengthWeightKg).append(",")
            append("\"latestWeightKg\":").append(stats.latestWeightKg ?: "null").append(",")
            append("\"weightChangeSinceLastRecordKg\":").append(stats.weightChangeSinceLastRecordKg ?: "null").append(",")
            append("\"latestBodyFatPercentage\":").append(stats.latestBodyFatPercentage ?: "null").append(",")
            append("\"latestWaistCm\":").append(stats.latestWaistCm ?: "null").append(",")
            append("\"latestSleepHours\":").append(stats.latestSleepHours ?: "null").append(",")
            append("\"latestFatigueScore\":").append(stats.latestFatigueScore ?: "null")
            append("}")
            append("}")
        }
    }

    private fun quote(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
    }
}
