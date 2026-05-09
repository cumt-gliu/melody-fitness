package com.giannisliu.melodyfitness.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.giannisliu.melodyfitness.data.repository.BodyMetricSummary
import com.giannisliu.melodyfitness.data.repository.BodyMetricInput
import com.giannisliu.melodyfitness.data.repository.FitnessRepository
import com.giannisliu.melodyfitness.data.repository.GoalInput
import com.giannisliu.melodyfitness.data.repository.GoalSummary
import com.giannisliu.melodyfitness.data.repository.HomeSnapshot
import com.giannisliu.melodyfitness.data.repository.StatsSnapshot
import com.giannisliu.melodyfitness.data.repository.StrengthWorkoutInput
import com.giannisliu.melodyfitness.data.repository.CardioWorkoutInput
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseTemplate
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import com.giannisliu.melodyfitness.data.repository.WorkoutHistoryItem
import com.giannisliu.melodyfitness.data.repository.WorkoutSummary
import com.giannisliu.melodyfitness.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val weeklyWorkoutCount: Int = 0,
    val latestWeightKg: Float? = null,
    val latestBodyMetric: BodyMetricSummary? = null,
    val recentWorkouts: List<WorkoutSummary> = emptyList(),
)

data class GoalsUiState(
    val goals: List<GoalSummary> = emptyList(),
)

data class HistoryUiState(
    val workouts: List<WorkoutHistoryItem> = emptyList(),
)

data class RecordUiState(
    val strengthTemplates: List<StrengthExerciseTemplate> = emptyList(),
)

data class SettingsUiState(
    val weightUnit: WeightUnit = WeightUnit.KG,
)

data class StatsUiState(
    val weeklyWorkoutCount: Int = 0,
    val totalCardioMinutes: Int = 0,
    val totalCardioDistanceKm: Float = 0f,
    val bestStrengthWeightKg: Float = 0f,
    val latestWeightKg: Float? = null,
    val weightChangeSinceLastRecordKg: Float? = null,
    val latestBodyFatPercentage: Float? = null,
    val latestWaistCm: Float? = null,
    val latestSleepHours: Float? = null,
    val latestFatigueScore: Int? = null,
)

class FitnessViewModel(
    private val repository: FitnessRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val homeUiState: StateFlow<HomeUiState> = repository.observeHomeSnapshot()
        .map(HomeSnapshot::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    val goalsUiState: StateFlow<GoalsUiState> = repository.observeGoals()
        .map { GoalsUiState(goals = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GoalsUiState(),
        )

    val historyUiState: StateFlow<HistoryUiState> = repository.observeWorkoutHistory()
        .map { HistoryUiState(workouts = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    val recordUiState: StateFlow<RecordUiState> = repository.observeStrengthTemplates()
        .map { RecordUiState(strengthTemplates = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordUiState(),
        )

    val statsUiState: StateFlow<StatsUiState> = repository.observeStatsSnapshot()
        .map(StatsSnapshot::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    val settingsUiState: StateFlow<SettingsUiState> = settingsRepository.observeWeightUnit()
        .map { SettingsUiState(weightUnit = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun saveStrengthWorkout(input: StrengthWorkoutInput) {
        viewModelScope.launch {
            repository.saveStrengthWorkout(input)
        }
    }

    fun saveCardioWorkout(input: CardioWorkoutInput) {
        viewModelScope.launch {
            repository.saveCardioWorkout(input)
        }
    }

    fun saveBodyMetric(input: BodyMetricInput) {
        viewModelScope.launch {
            repository.saveBodyMetric(input)
        }
    }

    fun saveGoal(input: GoalInput) {
        viewModelScope.launch {
            repository.saveGoal(input)
        }
    }

    fun updateWorkoutHistoryItem(
        workoutId: Long,
        title: String,
        notes: String,
    ) {
        viewModelScope.launch {
            repository.updateWorkoutHistoryItem(
                workoutId = workoutId,
                title = title,
                notes = notes,
            )
        }
    }

    fun deleteWorkoutHistoryItem(workoutId: Long) {
        viewModelScope.launch {
            repository.deleteWorkoutHistoryItem(workoutId)
        }
    }

    fun updateWeightUnit(weightUnit: WeightUnit) {
        settingsRepository.updateWeightUnit(weightUnit)
    }

    suspend fun exportBackupJson(): String {
        return repository.exportBackupJson(settingsUiState.value.weightUnit)
    }

    companion object {
        fun Factory(
            repository: FitnessRepository,
            settingsRepository: SettingsRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FitnessViewModel(repository, settingsRepository) as T
                }
            }
        }
    }
}

private fun HomeSnapshot.toUiState(): HomeUiState {
    return HomeUiState(
        weeklyWorkoutCount = weeklyWorkoutCount,
        latestWeightKg = latestWeightKg,
        latestBodyMetric = latestBodyMetric,
        recentWorkouts = recentWorkouts,
    )
}

private fun StatsSnapshot.toUiState(): StatsUiState {
    return StatsUiState(
        weeklyWorkoutCount = weeklyWorkoutCount,
        totalCardioMinutes = totalCardioMinutes,
        totalCardioDistanceKm = totalCardioDistanceKm,
        bestStrengthWeightKg = bestStrengthWeightKg,
        latestWeightKg = latestWeightKg,
        weightChangeSinceLastRecordKg = weightChangeSinceLastRecordKg,
        latestBodyFatPercentage = latestBodyFatPercentage,
        latestWaistCm = latestWaistCm,
        latestSleepHours = latestSleepHours,
        latestFatigueScore = latestFatigueScore,
    )
}
