package com.giannisliu.melodyfitness.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.giannisliu.melodyfitness.data.repository.BodyMetricSummary
import com.giannisliu.melodyfitness.data.repository.BodyMetricInput
import com.giannisliu.melodyfitness.data.repository.BodyMetricTrendPoint
import com.giannisliu.melodyfitness.data.repository.CardioDurationPoint
import com.giannisliu.melodyfitness.data.repository.CardioWorkoutInput
import com.giannisliu.melodyfitness.data.repository.FitnessRepository
import com.giannisliu.melodyfitness.data.repository.GoalInput
import com.giannisliu.melodyfitness.data.repository.GoalSummary
import com.giannisliu.melodyfitness.data.repository.HomeSnapshot
import com.giannisliu.melodyfitness.data.repository.StatsSnapshot
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseTemplate
import com.giannisliu.melodyfitness.data.repository.StrengthTrendPoint
import com.giannisliu.melodyfitness.data.repository.SparklineData
import com.giannisliu.melodyfitness.data.repository.StrengthWorkoutInput
import com.giannisliu.melodyfitness.data.repository.WeekOverWeekChanges
import com.giannisliu.melodyfitness.data.repository.WeeklyWorkoutCount
import com.giannisliu.melodyfitness.data.repository.WeeklyTrainingComparison
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import com.giannisliu.melodyfitness.data.repository.WorkoutHistoryEditInput
import com.giannisliu.melodyfitness.data.repository.WorkoutHistoryItem
import com.giannisliu.melodyfitness.data.repository.WorkoutSummary
import com.giannisliu.melodyfitness.data.repository.WorkoutTypeCount
import com.giannisliu.melodyfitness.data.settings.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

enum class TimeRange(val label: String) {
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    THIS_QUARTER("本季度"),
    THIS_YEAR("今年"),
}

fun TimeRange.toDateRange(): Pair<Long, Long> {
    val now = LocalDate.now()
    val start = when (this) {
        TimeRange.THIS_WEEK -> now.with(java.time.DayOfWeek.MONDAY)
        TimeRange.THIS_MONTH -> now.withDayOfMonth(1)
        TimeRange.THIS_QUARTER -> now.with(now.month.firstMonthOfQuarter()).withDayOfMonth(1)
        TimeRange.THIS_YEAR -> now.withDayOfYear(1)
    }
    return start.toEpochDay() to now.toEpochDay()
}

enum class StatsTab(val label: String) {
    TRAINING("训练分析"),
    BODY("身体指标"),
    STRENGTH("力量进步"),
}

data class StatsUiState(
    // Overview
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
    // Overview (new)
    val weekOverWeekChanges: WeekOverWeekChanges = WeekOverWeekChanges(),
    val bodyMetricTrend: List<BodyMetricTrendPoint> = emptyList(),
    val weeklyWorkoutCounts: List<WeeklyWorkoutCount> = emptyList(),
    // Tab state
    val selectedTimeRange: TimeRange = TimeRange.THIS_WEEK,
    val activeTab: StatsTab = StatsTab.TRAINING,
    // Tab 1: Training analysis
    val workoutTypeDistribution: List<WorkoutTypeCount> = emptyList(),
    val cardioDurationTrend: List<CardioDurationPoint> = emptyList(),
    val weeklyTrainingComparison: List<WeeklyTrainingComparison> = emptyList(),
    // Tab 3: Strength progress
    val strengthExerciseTrends: Map<String, List<StrengthTrendPoint>> = emptyMap(),
    val strengthExerciseNames: List<String> = emptyList(),
    val selectedExercise: String = "",
    // Visual polish
    val sparklineData: SparklineData = SparklineData(),
    val isReady: Boolean = false,
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

    private val selectedTimeRange = MutableStateFlow(TimeRange.THIS_WEEK)
    private val activeTab = MutableStateFlow(StatsTab.TRAINING)
    private val selectedExercise = MutableStateFlow("")

    // Intermediate container to avoid >5 typed combine params
    private data class StatsMeta(
        val snapshot: StatsSnapshot = StatsSnapshot(0, 0, 0f, 0f, null, null, null, null, null, null),
        val wow: WeekOverWeekChanges = WeekOverWeekChanges(),
        val bodyTrend: List<BodyMetricTrendPoint> = emptyList(),
        val timeRange: TimeRange = TimeRange.THIS_WEEK,
        val tab: StatsTab = StatsTab.TRAINING,
    )

    private val statsMeta: StateFlow<StatsMeta> = combine(
        repository.observeStatsSnapshot(),
        repository.observeWeekOverWeekChanges(),
        repository.observeBodyMetricTrend(),
        selectedTimeRange,
        activeTab,
    ) { snapshot, wow, bodyTrend, range, tab ->
        StatsMeta(snapshot, wow, bodyTrend, range, tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsMeta())

    // Tab 1: training analysis data
    private data class TabTrainingData(
        val distribution: List<WorkoutTypeCount> = emptyList(),
        val cardioTrend: List<CardioDurationPoint> = emptyList(),
        val weeklyCounts: List<WeeklyWorkoutCount> = emptyList(),
        val weeklyComparison: List<WeeklyTrainingComparison> = emptyList(),
    )

    private val tabTrainingData: StateFlow<TabTrainingData> = selectedTimeRange
        .flatMapLatest { range ->
            val (start, end) = range.toDateRange()
            combine(
                repository.observeCardioByDateRange(start, end),
                repository.observeCardioDurationTrend(start, end),
                repository.observeWeeklyWorkoutCounts(weeks = 8),
                repository.observeWeeklyTrainingComparison(weeks = 8),
            ) { distribution, cardioTrend, weeklyCounts, weeklyComparison ->
                TabTrainingData(distribution, cardioTrend, weeklyCounts, weeklyComparison)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabTrainingData())

    // Tab 3: strength data
    private val tabStrengthData: StateFlow<Map<String, List<StrengthTrendPoint>>> = selectedTimeRange
        .flatMapLatest { range ->
            val (start, end) = range.toDateRange()
            repository.observeStrengthTrend(start, end)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val sparklineData: StateFlow<SparklineData> = repository.observeSparklineData()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SparklineData())

    val statsUiState: StateFlow<StatsUiState> = combine(
        statsMeta,
        tabTrainingData,
        tabStrengthData,
        selectedExercise,
        sparklineData,
    ) { meta, trainingData, strengthMap, selEx, spark ->
        val base = meta.snapshot.toUiState()
        val exerciseNames = strengthMap.keys.toList()
        val effectiveExercise = if (selEx.isBlank() && exerciseNames.isNotEmpty())
            exerciseNames.first() else selEx
        base.copy(
            weekOverWeekChanges = meta.wow,
            selectedTimeRange = meta.timeRange,
            activeTab = meta.tab,
            bodyMetricTrend = meta.bodyTrend,
            workoutTypeDistribution = trainingData.distribution,
            cardioDurationTrend = trainingData.cardioTrend,
            weeklyWorkoutCounts = trainingData.weeklyCounts,
            weeklyTrainingComparison = trainingData.weeklyComparison,
            strengthExerciseTrends = strengthMap,
            strengthExerciseNames = exerciseNames,
            selectedExercise = effectiveExercise,
            sparklineData = spark,
            isReady = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun updateTimeRange(range: TimeRange) {
        selectedTimeRange.value = range
    }

    fun updateActiveTab(tab: StatsTab) {
        activeTab.value = tab
    }

    fun selectExercise(name: String) {
        selectedExercise.value = name
    }

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

    fun duplicateWorkoutHistoryItem(workoutId: Long) {
        viewModelScope.launch {
            repository.duplicateWorkoutHistoryItem(workoutId)
        }
    }

    fun updateWorkoutHistoryDetails(
        workoutId: Long,
        input: WorkoutHistoryEditInput,
    ) {
        viewModelScope.launch {
            repository.updateWorkoutHistoryDetails(workoutId, input)
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
