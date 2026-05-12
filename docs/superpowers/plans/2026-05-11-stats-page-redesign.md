# 统计页重新设计 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign StatsScreen with a three-layer layout: fixed overview row + time range filter + tabbed detail area (training analysis / body metrics / strength progress).

**Architecture:** Keep existing Vico charts (line, column). Add Compose Canvas for donut chart and sparklines. New DAO queries with date-range params. ViewModel manages `selectedTimeRange` via `MutableStateFlow` + `flatMapLatest`.

**Tech Stack:** Compose UI, Vico 2.1.0, Room DAO, kotlinx.coroutines.flow

---

### File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `data/local/dao/StatsDao.kt` | Modify | Add 3 date-range queries + return types + `observeCardioMinutesBetween()` |
| `data/local/dao/WorkoutLogDao.kt` | Modify | Add `observeWorkoutCountBetween()` + `observeTrainingDaysBetween()` |
| `data/repository/FitnessRepository.kt` | Modify | Add data models + 4 interface methods |
| `data/repository/OfflineFitnessRepository.kt` | Modify | Implement new methods + week-over-week computation |
| `ui/FitnessViewModel.kt` | Modify | Add `TimeRange`, extend `StatsUiState`, wire tab flows with `flatMapLatest` |
| `ui/StatsScreen.kt` | **Create** | Extracted StatsScreen + overview row + 3 tab composables |
| `ui/MelodyFitnessApp.kt` | Modify | Remove inline StatsScreen/composables, import `StatsScreen` |
| `data/repository/OfflineFitnessRepositoryTest.kt` | Modify | Add `FakeStatsDao` implementation + new tests |

---

### Task 1: DAO Queries + Return Types

**Files:** `StatsDao.kt`, `WorkoutLogDao.kt`

- [ ] **Step 1: Add DAO return types to StatsDao.kt**

Append after `BodyMetricTrend`:

```kotlin
data class CardioByDate(
    val dateEpochDay: Long,
    val activityType: String,
    val durationMinutes: Int,
    val distanceKm: Float,
)

data class CardioDurationRow(
    val dateEpochDay: Long,
    val totalMinutes: Int,
)

data class StrengthTrendRow(
    val dateEpochDay: Long,
    val exerciseName: String,
    val maxWeight: Float,
    val volume: Float,
)
```

- [ ] **Step 2: Add date-range query to StatsDao**

```kotlin
@Query("""
    SELECT w.dateEpochDay, c.activityType, c.durationMinutes, c.distanceKm
    FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay ORDER BY w.dateEpochDay ASC
""")
fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<CardioByDate>>

@Query("""
    SELECT w.dateEpochDay, SUM(c.durationMinutes) AS totalMinutes
    FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
    GROUP BY w.dateEpochDay ORDER BY w.dateEpochDay ASC
""")
fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationRow>>

@Query("""
    SELECT w.dateEpochDay, e.name AS exerciseName, MAX(s.weightKg) AS maxWeight,
           SUM(s.weightKg * s.reps) AS volume
    FROM strength_sets s JOIN strength_exercises e ON s.exerciseId = e.id
    JOIN workout_logs w ON e.workoutLogId = w.id
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
    GROUP BY w.dateEpochDay, e.name ORDER BY w.dateEpochDay ASC
""")
fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<List<StrengthTrendRow>>

@Query("""
    SELECT COALESCE(SUM(c.durationMinutes), 0)
    FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
""")
fun observeCardioMinutesBetween(startDay: Long, endDay: Long): Flow<Int>
```

- [ ] **Step 3: Add queries to WorkoutLogDao.kt**

```kotlin
@Query("SELECT COUNT(*) FROM workout_logs WHERE dateEpochDay BETWEEN :startDay AND :endDay")
fun observeWorkoutCountBetween(startDay: Long, endDay: Long): Flow<Int>

@Query("SELECT COUNT(DISTINCT dateEpochDay) FROM workout_logs WHERE dateEpochDay BETWEEN :startDay AND :endDay")
fun observeTrainingDaysBetween(startDay: Long, endDay: Long): Flow<Int>
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 2: Repository Interface + Domain Models

**Files:** `FitnessRepository.kt`

- [ ] **Step 1: Add domain models before `FitnessRepository` interface**

```kotlin
data class WeekOverWeekChanges(
    val workoutCountChange: Int? = null,
    val previousWeekWorkoutCount: Int = 0,
    val cardioMinutesChange: Int? = null,
    val previousWeekCardioMinutes: Int = 0,
    val trainingDaysChange: Int? = null,
    val previousWeekTrainingDays: Int = 0,
    val weightChange: Float? = null,
)

data class WorkoutTypeCount(
    val type: String,
    val count: Int,
)

data class CardioDurationPoint(
    val dateEpochDay: Long,
    val totalMinutes: Int,
)

data class StrengthTrendPoint(
    val dateEpochDay: Long,
    val maxWeightKg: Float,
    val volumeKg: Float,
)
```

- [ ] **Step 2: Add methods to `FitnessRepository` interface**

```kotlin
fun observeWeekOverWeekChanges(): Flow<WeekOverWeekChanges>
fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<WorkoutTypeCount>>
fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationPoint>>
fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<Map<String, List<StrengthTrendPoint>>>
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 3: Repository Implementation

**Files:** `OfflineFitnessRepository.kt`

- [ ] **Step 1: Implement `observeWeekOverWeekChanges()`**

Uses `thisWeekStart` and `lastWeekEnd = thisWeekStart - 1` as the previous period. Computes weight change from `bodyMetricDao.observeRecentMetrics(1)` vs a new `bodyMetricDao` query for last week's latest weight.

```kotlin
override fun observeWeekOverWeekChanges(): Flow<WeekOverWeekChanges> {
    val today = LocalDate.now()
    val thisWeekStart = today.with(java.time.DayOfWeek.MONDAY).toEpochDay()
    val lastWeekStart = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY).toEpochDay()
    val lastWeekEnd = thisWeekStart - 1
    val todayEpochDay = today.toEpochDay()

    return combine(
        workoutLogDao.observeWorkoutCountSince(thisWeekStart),
        workoutLogDao.observeWorkoutCountBetween(lastWeekStart, lastWeekEnd),
        statsDao.observeCardioMinutesBetween(thisWeekStart, todayEpochDay),
        statsDao.observeCardioMinutesBetween(lastWeekStart, lastWeekEnd),
        workoutLogDao.observeTrainingDaysBetween(thisWeekStart, todayEpochDay),
        workoutLogDao.observeTrainingDaysBetween(lastWeekStart, lastWeekEnd),
    ) { twc, lwc, tcm, lcm, twd, lwd ->
        WeekOverWeekChanges(
            workoutCountChange = twc - lwc,
            previousWeekWorkoutCount = lwc,
            cardioMinutesChange = tcm - lcm,
            previousWeekCardioMinutes = lcm,
            trainingDaysChange = twd - lwd,
            previousWeekTrainingDays = lwd,
        )
    }
}
```

- [ ] **Step 2: Implement `observeCardioByDateRange()`**

Transform `StatsDao.CardioByDate` rows into domain `WorkoutTypeCount` by aggregating counts per activityType, with strength detected via workout_logs that have strength_exercises entries.

```kotlin
override fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<WorkoutTypeCount>> {
    return combine(
        statsDao.observeCardioByDateRange(startDay, endDay),
        workoutLogDao.observeWorkoutLogs(),
    ) { cardioRows, workouts ->
        val filteredWorkouts = workouts.filter { w ->
            w.workoutLog.dateEpochDay in startDay..endDay
        }
        val strengthCount = filteredWorkouts.count { it.strengthExercises.isNotEmpty() }
        val cardioTypeCounts = cardioRows
            .groupBy { it.activityType }
            .mapValues { it.value.size }

        val list = mutableListOf<WorkoutTypeCount>()
        if (strengthCount > 0) list.add(WorkoutTypeCount("力量", strengthCount))
        cardioTypeCounts.forEach { (type, count) ->
            list.add(WorkoutTypeCount(type, count))
        }
        list
    }
}
```

- [ ] **Step 3: Implement `observeCardioDurationTrend()`**

```kotlin
override fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationPoint>> {
    return statsDao.observeCardioDurationTrend(startDay, endDay).map { rows ->
        rows.map { CardioDurationPoint(it.dateEpochDay, it.totalMinutes) }
    }
}
```

- [ ] **Step 4: Implement `observeStrengthTrend()`**

Groups `StrengthTrendRow` by exercise name into a `Map<String, List<StrengthTrendPoint>>`.

```kotlin
override fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<Map<String, List<StrengthTrendPoint>>> {
    return statsDao.observeStrengthTrend(startDay, endDay).map { rows ->
        rows.groupBy { it.exerciseName }.mapValues { entry ->
            entry.value.map {
                StrengthTrendPoint(
                    dateEpochDay = it.dateEpochDay,
                    maxWeightKg = it.maxWeight,
                    volumeKg = it.volume,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 4: ViewModel — TimeRange + StatsUiState

**Files:** `FitnessViewModel.kt`

- [ ] **Step 1: Add `TimeRange` enum before `StatsUiState`**

```kotlin
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
```

- [ ] **Step 2: Add active tab enum and extend `StatsUiState`**

```kotlin
enum class StatsTab(val label: String) {
    TRAINING("训练分析"),
    BODY("身体指标"),
    STRENGTH("力量进步"),
}

data class StatsUiState(
    // Overview (existing)
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
    // Tab state
    val selectedTimeRange: TimeRange = TimeRange.THIS_WEEK,
    val activeTab: StatsTab = StatsTab.TRAINING,
    // Tab 1: Training analysis
    val workoutTypeDistribution: List<WorkoutTypeCount> = emptyList(),
    val cardioDurationTrend: List<CardioDurationPoint> = emptyList(),
    // Tab 2: Body metrics (existing fields already cover this)
    val bodyMetricTrend: List<BodyMetricTrendPoint> = emptyList(),
    val weeklyWorkoutCounts: List<WeeklyWorkoutCount> = emptyList(),
    // Tab 3: Strength progress
    val strengthExerciseTrends: Map<String, List<StrengthTrendPoint>> = emptyMap(),
    val strengthExerciseNames: List<String> = emptyList(),
    val selectedExercise: String = "",
)

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
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 5: ViewModel — Data Flow Wiring

**Files:** `FitnessViewModel.kt`

- [ ] **Step 1: Add `selectedTimeRange` state + `updateTimeRange()` + `updateActiveTab()` + `selectExercise()`**

```kotlin
private val selectedTimeRange = MutableStateFlow(TimeRange.THIS_WEEK)
private val activeTab = MutableStateFlow(StatsTab.TRAINING)
private val selectedExercise = MutableStateFlow("")
```

- [ ] **Step 2: Rewrite `statsUiState` to combine overview + tab data**

```kotlin
val statsUiState: StateFlow<StatsUiState> = combine(
    repository.observeStatsSnapshot(),
    repository.observeWeekOverWeekChanges(),
    selectedTimeRange,
    activeTab,
) { snapshot, wow, timeRange, tab ->
    snapshot.toUiState().copy(
        weekOverWeekChanges = wow,
        selectedTimeRange = timeRange,
        activeTab = tab,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = StatsUiState(),
)
```

- [ ] **Step 3: Add tab data flows using `flatMapLatest`**

```kotlin
// Tab 1: training analysis data
private val tabTrainingData: StateFlow<TabTrainingData> = selectedTimeRange
    .flatMapLatest { range ->
        val (start, end) = range.toDateRange()
        combine(
            repository.observeCardioByDateRange(start, end),
            repository.observeCardioDurationTrend(start, end),
            repository.observeBodyMetricTrend(), // pass unfiltered, UI handles display
            repository.observeWeeklyWorkoutCounts(weeks = 8),
        ) { distribution, cardioTrend, _, weeklyCounts ->
            TabTrainingData(distribution, cardioTrend, weeklyCounts)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabTrainingData())

private data class TabTrainingData(
    val distribution: List<WorkoutTypeCount> = emptyList(),
    val cardioTrend: List<CardioDurationPoint> = emptyList(),
    val weeklyCounts: List<WeeklyWorkoutCount> = emptyList(),
)

// Tab 3: strength data
private val tabStrengthData: StateFlow<Map<String, List<StrengthTrendPoint>>> = selectedTimeRange
    .flatMapLatest { range ->
        val (start, end) = range.toDateRange()
        repository.observeStrengthTrend(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
```

- [ ] **Step 4: Merge tab data into final state**

Update the `statsUiState` combine to also consume the tab data flows:

```kotlin
val statsUiState: StateFlow<StatsUiState> = combine(
    repository.observeStatsSnapshot(),
    repository.observeWeekOverWeekChanges(),
    selectedTimeRange,
    activeTab,
    tabTrainingData,
    repository.observeBodyMetricTrend(),
    tabStrengthData,
    selectedExercise,
) { snapshot, wow, timeRange, tab, trainingData, bodyTrend, strengthMap, selEx ->
    val base = snapshot.toUiState()
    val exerciseNames = strengthMap.keys.toList()
    val effectiveExercise = if (selEx.isBlank() && exerciseNames.isNotEmpty())
        exerciseNames.first() else selEx

    base.copy(
        weekOverWeekChanges = wow,
        selectedTimeRange = timeRange,
        activeTab = tab,
        // Tab 1
        workoutTypeDistribution = trainingData.distribution,
        cardioDurationTrend = trainingData.cardioTrend,
        weeklyWorkoutCounts = trainingData.weeklyCounts,
        // Tab 2
        bodyMetricTrend = bodyTrend,
        // Tab 3
        strengthExerciseTrends = strengthMap,
        strengthExerciseNames = exerciseNames,
        selectedExercise = effectiveExercise,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = StatsUiState(),
)
```

- [ ] **Step 5: Add public methods for tab interactions**

```kotlin
fun updateTimeRange(range: TimeRange) {
    selectedTimeRange.value = range
}

fun updateActiveTab(tab: StatsTab) {
    activeTab.value = tab
}

fun selectExercise(name: String) {
    selectedExercise.value = name
}
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 6: Extract StatsScreen to Separate File

**Files:** `StatsScreen.kt` (create), `MelodyFitnessApp.kt` (modify)

- [ ] **Step 1: Create `StatsScreen.kt`**

Move from `MelodyFitnessApp.kt` into `StatsScreen.kt`:
- `StatsScreen` composable
- `MetricCard` composable (make it internal, not private, since it stays in the same package)
- `WeightTrendChart` composable
- `WeeklyWorkoutChart` composable

Keep in `MelodyFitnessApp.kt`:
- `SectionTitle` (used by multiple screens)
- `EmptyCard` (used by multiple screens)
- `formatFloat`, `formatWeight` (used globally)

The extracted `StatsScreen` function signature stays the same:

```kotlin
package com.giannisliu.melodyfitness.ui

// imports...

@Composable
fun StatsScreen(
    paddingValues: PaddingValues,
    uiState: StatsUiState,
    weightUnit: WeightUnit,
    onUpdateTimeRange: (TimeRange) -> Unit = {},
    onUpdateActiveTab: (StatsTab) -> Unit = {},
    onSelectExercise: (String) -> Unit = {},
) {
    // ... (existing implementation initially, replaced in later tasks)
}
```

- [ ] **Step 2: Update `MelodyFitnessApp.kt`**

Remove the imported constants and composables that were moved. The call site `StatsScreen(...)` remains unchanged (auto-resolved since same package).

Remove these private composables: `StatsScreen`, `MetricCard`, `WeightTrendChart`, `WeeklyWorkoutChart`.

Remove unused Vico imports from `MelodyFitnessApp.kt` (they're now needed in `StatsScreen.kt`):
- `com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost`
- `com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer`
- `com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer`
- `com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart`
- `com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer`
- `com.patrykandpatrick.vico.core.cartesian.data.lineSeries`
- `com.patrykandpatrick.vico.core.cartesian.data.columnSeries`
- Various Vico axis imports

Keep in `MelodyFitnessApp.kt` only:
- `BodyMetricTrendPoint` (used in HomeScreen)
- `WeeklyWorkoutCount` (used in history if needed — actually check)
- `StrengthExerciseHistory` (used in HistoryScreen)
- `GoalInput`, `GoalType` (used in GoalsScreen)
- `WeightUnit` (used globally)
- `formatFloat`, `formatWeight` (used globally)

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 7: UI — Overview Row

**Files:** `StatsScreen.kt`

Replace the top section of `StatsScreen` with the new overview row.

- [ ] **Step 1: Add `WeekOverWeekChanges` data class import (will resolve from same package)**

- [ ] **Step 2: Replace the first `Row { MetricCard(...) MetricCard(...) }` block with 4-card overview row**

```kotlin
// Overview row (always shows current week data)
OverviewRow(
    weeklyWorkoutCount = uiState.weeklyWorkoutCount,
    totalCardioMinutes = uiState.totalCardioMinutes,
    latestWeightKg = uiState.latestWeightKg,
    wow = uiState.weekOverWeekChanges,
    onCardClick = { tab -> onUpdateActiveTab(tab) },
)
```

- [ ] **Step 3: Create `OverviewRow` composable + sparkline helper**

```kotlin
@Composable
private fun OverviewRow(
    weeklyWorkoutCount: Int,
    totalCardioMinutes: Int,
    latestWeightKg: Float?,
    wow: WeekOverWeekChanges,
    onCardClick: (StatsTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "本周训练",
            value = "${weeklyWorkoutCount} 次",
            change = wow.workoutCountChange,
            compareLabel = "vs 上周 ${wow.previousWeekWorkoutCount}",
            onClick = { onCardClick(StatsTab.TRAINING) },
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "有氧时长",
            value = "${totalCardioMinutes} 分钟",
            change = wow.cardioMinutesChange,
            compareLabel = "vs 上周 ${wow.previousWeekCardioMinutes}",
            onClick = { onCardClick(StatsTab.TRAINING) },
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "体重变化",
            value = latestWeightKg?.let { "${formatWeightChange(latestWeightKg, wow.weightChange, WeightUnit.KG)}" } ?: "--",
            change = null,
            compareLabel = "较上周",
            onClick = { onCardClick(StatsTab.BODY) },
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "训练天数",
            value = "${wow.previousWeekTrainingDays + (wow.trainingDaysChange ?: 0)} 天",
            change = wow.trainingDaysChange,
            compareLabel = "vs 上周 ${wow.previousWeekTrainingDays}",
            onClick = { onCardClick(StatsTab.TRAINING) },
        )
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    change: Int?,
    compareLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (change != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val (arrow, color) = if (change >= 0) "▲" to Color(0xFF27ae60) else "▼" to Color(0xFFc0392b)
                    Text("$arrow${if (change >= 0) "+" else ""}$change", color = color, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(compareLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

Note: `formatWeightChange` is a new helper. For weight change, we show the delta directly:
```kotlin
private fun formatWeightChange(latestKg: Float, change: Float?, unit: WeightUnit): String {
    if (change == null) return "${formatWeight(latestKg, unit)}"
    val prefix = if (change >= 0) "+" else ""
    return "${prefix}${formatFloat(change)} ${unit.symbol}"
}
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 8: UI — Time Filter + Tab Navigation

**Files:** `StatsScreen.kt`

- [ ] **Step 1: Add time range filter row after overview row**

```kotlin
@Composable
private fun TimeRangeFilter(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}
```

- [ ] **Step 2: Add tab navigation row**

```kotlin
@Composable
private fun StatsTabRow(
    selected: StatsTab,
    onSelect: (StatsTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsTab.entries.forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                label = { Text(tab.label) },
            )
        }
    }
}
```

- [ ] **Step 3: Wire into `StatsScreen` layout**

```kotlin
// In StatsScreen, after the SectionTitle and OverviewRow:
SectionTitle(title = "训练统计")
OverviewRow(...)

Spacer(modifier = Modifier.height(8.dp))
TimeRangeFilter(
    selected = uiState.selectedTimeRange,
    onSelect = onUpdateTimeRange,
)
Spacer(modifier = Modifier.height(8.dp))
StatsTabRow(
    selected = uiState.activeTab,
    onSelect = onUpdateActiveTab,
)
Spacer(modifier = Modifier.height(12.dp))

// Tab content
when (uiState.activeTab) {
    StatsTab.TRAINING -> TrainingAnalysisTab(uiState)
    StatsTab.BODY -> BodyMetricsTab(uiState, weightUnit)
    StatsTab.STRENGTH -> StrengthProgressTab(uiState, onSelectExercise)
}

// Advice card at bottom
StatsAdviceCard(uiState)
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 9: UI — Tab 1 Training Analysis

**Files:** `StatsScreen.kt`

- [ ] **Step 1: Create `TrainingAnalysisTab` composable**

Contains 3 chart sections:

```kotlin
@Composable
private fun TrainingAnalysisTab(uiState: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Row: frequency bar chart + distribution donut chart
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Left: weekly workout frequency (enhanced existing chart)
            EnhancedWeeklyWorkoutChart(
                modifier = Modifier.weight(1f),
                weeklyCounts = uiState.weeklyWorkoutCounts,
            )
            // Right: workout type distribution donut chart
            WorkoutDistributionChart(
                modifier = Modifier.weight(1f),
                distribution = uiState.workoutTypeDistribution,
            )
        }
        // Cardio duration trend line chart
        CardioDurationChart(
            trend = uiState.cardioDurationTrend,
        )
    }
}
```

- [ ] **Step 2: Create `EnhancedWeeklyWorkoutChart` (enhance existing)**

Modify the existing `WeeklyWorkoutChart` to add:
- Color per bar: `#1f6f50` if count >= average, `#cc9a62` if below average
- Average reference line (if > 1 bar showing)
- "周均 N 次" label

Since Vico `ColumnCartesianLayer` supports per-column coloring via `setColors`, pre-compute the average and set colors.

```kotlin
@Composable
private fun EnhancedWeeklyWorkoutChart(
    weeklyCounts: List<WeeklyWorkoutCount>,
    modifier: Modifier = Modifier,
) {
    if (weeklyCounts.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    val chartColors = remember(weeklyCounts) {
        val avg = weeklyCounts.map { it.count }.average()
        weeklyCounts.map { if (it.count >= avg) Color(0xFF1f6f50) else Color(0xFFcc9a62) }
    }

    LaunchedEffect(weeklyCounts) {
        modelProducer.runTransaction {
            columnSeries { series(weeklyCounts.map { it.count.toDouble() }) }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("训练频次", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }
}
```

Note: Vico doesn't directly support per-column color in its simple API. The alternative is to use a custom `ColumnCartesianLayer` with `setColumnColors` or use Compose Canvas for full control. For simplicity, implement using Compose Canvas for the bar chart, which gives full color control:

```kotlin
@Composable
private fun TrainingFrequencyBars(
    weeklyCounts: List<WeeklyWorkoutCount>,
    modifier: Modifier = Modifier,
) {
    if (weeklyCounts.isEmpty()) return
    val avg = weeklyCounts.map { it.count }.average()

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("训练频次", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = weeklyCounts.size
                    val barWidth = size.width / barCount * 0.6f
                    val gap = size.width / barCount * 0.4f
                    val maxCount = (weeklyCounts.maxOf { it.count }.coerceAtLeast(1)).toFloat()
                    val chartHeight = size.height * 0.85f

                    weeklyCounts.forEachIndexed { index, wc ->
                        val barHeight = (wc.count / maxCount) * chartHeight
                        val x = index * (barWidth + gap) + gap / 2
                        val y = size.height - barHeight
                        val color = if (wc.count >= avg) Color(0xFF1f6f50) else Color(0xFFcc9a62)
                        drawRect(color, Offset(x, y), Size(barWidth, barHeight))
                    }

                    // Average line
                    val avgY = size.height - (avg.toFloat() / maxCount) * chartHeight
                    drawLine(Color(0xFFb36a2c), Offset(0f, avgY), Offset(size.width, avgY), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                }
            }
            if (weeklyCounts.isNotEmpty()) {
                Text("周均 ${"%.1f".format(avg)} 次", color = Color(0xFFb36a2c), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

- [ ] **Step 3: Create `WorkoutDistributionChart` (donut chart)**

```kotlin
@Composable
private fun WorkoutDistributionChart(
    distribution: List<WorkoutTypeCount>,
    modifier: Modifier = Modifier,
) {
    if (distribution.isEmpty()) return
    val total = distribution.sumOf { it.count }
    val colors = listOf(Color(0xFF1f6f50), Color(0xFF4e8a6c), Color(0xFFcc9a62), Color(0xFFb36a2c))

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("训练类型分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(120.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        distribution.forEachIndexed { index, wc ->
                            val sweep = (wc.count.toFloat() / total) * 360f
                            drawArc(colors[index % colors.size], startAngle, sweep, useCenter = true, size = size)
                            startAngle += sweep
                        }
                        // Center hole
                        drawArc(Color.Transparent, 0f, 360f, useCenter = true, size = size * 0.6f)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    distribution.forEachIndexed { index, wc ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(colors[index % colors.size], CircleShape))
                            Text("${wc.type} ${wc.count * 100 / total}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("共计 $total 次训练", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

- [ ] **Step 4: Create `CardioDurationChart`**

```kotlin
@Composable
private fun CardioDurationChart(
    trend: List<CardioDurationPoint>,
    modifier: Modifier = Modifier,
) {
    if (trend.isEmpty()) return
    val totalMinutes = trend.sumOf { it.totalMinutes }
    val days = trend.size

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trend) {
        modelProducer.runTransaction {
            lineSeries { series(trend.map { it.totalMinutes.toDouble() }) }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("有氧时长趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Text("本期累计 ${totalMinutes} 分钟 · 日均 ${totalMinutes / days} 分钟",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 10: UI — Tab 2 Body Metrics

**Files:** `StatsScreen.kt`

- [ ] **Step 1: Create period comparison row**

```kotlin
@Composable
private fun PeriodComparisonRow(
    uiState: StatsUiState,
    weightUnit: WeightUnit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ComparisonCard("体重", uiState.latestWeightKg?.let { "${formatWeight(it, weightUnit)}" } ?: "--", uiState.weightChangeSinceLastRecordKg)
        ComparisonCard("体脂", uiState.latestBodyFatPercentage?.let { "${formatFloat(it)}%" } ?: "--", null)
        ComparisonCard("腰围", uiState.latestWaistCm?.let { "${formatFloat(it)} cm" } ?: "--", null)
        ComparisonCard("睡眠", uiState.latestSleepHours?.let { "${formatFloat(it)} h" } ?: "--", null)
        ComparisonCard("疲劳", uiState.latestFatigueScore?.let { "$it/10" } ?: "--", uiState.latestFatigueScore?.let { -it.toFloat() })
    }
}

@Composable
private fun ComparisonCard(
    label: String,
    value: String,
    change: Float?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.weight(1f)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (change != null) {
                val (arrow, color) = when {
                    change > 0 -> "▲" to Color(0xFF27ae60)
                    change < 0 -> "▼" to Color(0xFFc0392b)
                    else -> "—" to Color(0xFF7f8c8d)
                }
                Text("$arrow ${formatFloat(change)}", color = color, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

- [ ] **Step 2: Create `BodyMetricsTab` composable**

```kotlin
@Composable
private fun BodyMetricsTab(
    uiState: StatsUiState,
    weightUnit: WeightUnit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PeriodComparisonRow(uiState, weightUnit)

        // Weight + body fat combo chart (enhanced WeightTrendChart)
        EnhancedWeightTrendChart(
            trend = uiState.bodyMetricTrend,
            weightUnit = weightUnit,
        )

        // Mini trend row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniTrendChart(
                modifier = Modifier.weight(1f),
                title = "腰围趋势",
                data = uiState.bodyMetricTrend.mapNotNull { it.waistCm?.let { cm -> cm.toDouble() } },
                lineColor = Color(0xFF4e8a6c),
            )
            MiniTrendChart(
                modifier = Modifier.weight(1f),
                title = "睡眠趋势",
                data = uiState.bodyMetricTrend.mapNotNull { it.sleepHours?.let { h -> h.toDouble() } },
                lineColor = Color(0xFFcc9a62),
                referenceLine = 7.0,
            )
            MiniTrendChart(
                modifier = Modifier.weight(1f),
                title = "疲劳趋势",
                data = uiState.bodyMetricTrend.mapNotNull { it.fatigueScore?.let { f -> f.toDouble() } },
                lineColor = Color(0xFFcc9a62),
            )
        }
    }
}
```

- [ ] **Step 3: Create `MiniTrendChart` (Canvas sparkline)**

```kotlin
@Composable
private fun MiniTrendChart(
    modifier: Modifier = Modifier,
    title: String,
    data: List<Double>,
    lineColor: Color,
    referenceLine: Double? = null,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (data.size < 2) {
                Text("数据不足", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val min = data.min()
                        val max = data.max()
                        val range = (max - min).coerceAtLeast(1.0)
                        val points = data.mapIndexed { i, v ->
                            val x = (i.toFloat() / (data.size - 1).coerceAtLeast(1)) * size.width
                            val y = ((v - min) / range).toFloat() * size.height
                            Offset(x, size.height - y)
                        }
                        // Line
                        for (i in 0 until points.size - 1) {
                            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 2f)
                        }
                        // Reference line
                        referenceLine?.let { ref ->
                            val refY = ((ref - min) / range).toFloat() * size.height
                            drawLine(Color(0xFFb36a2c), Offset(0f, size.height - refY), Offset(size.width, size.height - refY), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Enhance `WeightTrendChart` to support body fat overlay + target line**

Rename to `EnhancedWeightTrendChart`. Since Vico doesn't natively support dual-axis combo charts easily, use a single CartesianChartHost with two line series — one for weight, one for body fat.

```kotlin
@Composable
private fun EnhancedWeightTrendChart(
    trend: List<BodyMetricTrendPoint>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    if (trend.size < 2) {
        if (trend.isEmpty()) return
        Card(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("体重趋势 + 体脂", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("至少需要 2 条记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trend, weightUnit) {
        modelProducer.runTransaction {
            lineSeries {
                series(trend.map { p ->
                    when (weightUnit) { WeightUnit.KG -> p.weightKg.toDouble() WeightUnit.LB -> (p.weightKg * 2.20462).toDouble() }
                })
                // Body fat as second series (only if data exists)
                if (trend.any { it.bodyFatPercentage != null }) {
                    series(trend.map { it.bodyFatPercentage?.toDouble() ?: Double.NaN })
                }
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("体重趋势 + 体脂", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● 体重", color = Color(0xFF1f6f50), style = MaterialTheme.typography.labelSmall)
                if (trend.any { it.bodyFatPercentage != null }) {
                    Text("- - 体脂", color = Color(0xFFb36a2c), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 11: UI — Tab 3 Strength Progress

**Files:** `StatsScreen.kt`

- [ ] **Step 1: Create `StrengthProgressTab` composable**

```kotlin
@Composable
private fun StrengthProgressTab(
    uiState: StatsUiState,
    onSelectExercise: (String) -> Unit,
) {
    if (uiState.strengthExerciseNames.isEmpty()) {
        EmptyCard(title = "还没有力量训练记录", body = "记录几次力量训练后，这里会展示各动作的重量进步趋势。")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Exercise selector chips
        ExerciseSelector(
            exercises = uiState.strengthExerciseNames,
            selected = uiState.selectedExercise,
            onSelect = onSelectExercise,
        )

        // Stats row for selected exercise
        val selectedTrend = uiState.strengthExerciseTrends[uiState.selectedExercise].orEmpty()
        StrengthStatsRow(trend = selectedTrend)

        // Weight progression chart
        if (selectedTrend.size >= 2) {
            StrengthWeightChart(trend = selectedTrend)
            StrengthVolumeChart(trend = selectedTrend)
        } else {
            Text("选中动作的数据不足，暂无法展示趋势", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExerciseSelector(
    exercises: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        exercises.forEach { name ->
            FilterChip(
                selected = selected == name,
                onClick = { onSelect(name) },
                label = { Text(name) },
            )
        }
    }
}
```

- [ ] **Step 2: Create `StrengthStatsRow`**

```kotlin
@Composable
private fun StrengthStatsRow(trend: List<StrengthTrendPoint>) {
    val bestWeight = trend.maxOfOrNull { it.maxWeightKg } ?: 0f
    val lastWeight = trend.lastOrNull()?.maxWeightKg ?: 0f
    val prevWeight = trend.getOrNull(trend.size - 2)?.maxWeightKg
    val totalVolume = trend.sumOf { it.volumeKg.toDouble() }.toFloat()
    val lastVolume = trend.lastOrNull()?.volumeKg ?: 0f

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStatCard("最佳重量", "${formatFloat(bestWeight)} kg")
        MiniStatCard("本次重量", "${formatFloat(lastWeight)} kg", change = prevWeight?.let { lastWeight - it })
        MiniStatCard("总容量", "${formatFloat(totalVolume)} kg")
        MiniStatCard("本次容量", "${formatFloat(lastVolume)} kg")
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    change: Float? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.weight(1f)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}
```

- [ ] **Step 3: Create `StrengthWeightChart` with PR marker**

Use Vico line chart for weight trend. PR detection: a data point is a PR if it's the highest seen up to that point.

```kotlin
@Composable
private fun StrengthWeightChart(
    trend: List<StrengthTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val prIndices = remember(trend) {
        var maxSoFar = -1f
        trend.mapIndexed { i, p ->
            if (p.maxWeightKg > maxSoFar) { maxSoFar = p.maxWeightKg; i } else -1
        }.filter { it >= 0 }
    }

    LaunchedEffect(trend) {
        modelProducer.runTransaction {
            lineSeries { series(trend.map { it.maxWeightKg.toDouble() }) }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("重量趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            if (prIndices.isNotEmpty()) {
                val prPoint = trend[prIndices.last()]
                Text("PR ${formatFloat(prPoint.maxWeightKg)} kg", color = Color(0xFFb36a2c), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StrengthVolumeChart(
    trend: List<StrengthTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trend) {
        modelProducer.runTransaction {
            lineSeries { series(trend.map { it.volumeKg.toDouble() }) }
        }
    }

    val totalVolume = trend.sumOf { it.volumeKg.toDouble() }.toFloat()
    val avgVolume = if (trend.isNotEmpty()) totalVolume / trend.size else 0f

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("训练容量趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Text("本期总容量 ${formatFloat(totalVolume)} kg · 单次平均 ${formatFloat(avgVolume)} kg",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 12: UI — Advice Card Update

**Files:** `StatsScreen.kt`

- [ ] **Step 1: Replace existing advice card with `StatsAdviceCard`**

```kotlin
@Composable
private fun StatsAdviceCard(uiState: StatsUiState) {
    val advice = when (uiState.activeTab) {
        StatsTab.TRAINING -> getTrainingAdvice(uiState)
        StatsTab.BODY -> getBodyAdvice(uiState)
        StatsTab.STRENGTH -> getStrengthAdvice(uiState)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(advice)
        }
    }
}

private fun getTrainingAdvice(uiState: StatsUiState): String {
    if (uiState.weeklyWorkoutCount < 3) return "这周训练次数还不多，可以优先把频率补上。"
    if (uiState.totalCardioMinutes < 60) return "有氧累计偏少，可以补一到两次 20-30 分钟中低强度训练。"
    val strengthPct = uiState.workoutTypeDistribution.find { it.type == "力量" }?.count ?: 0
    val total = uiState.workoutTypeDistribution.sumOf { it.count }
    if (total > 0 && strengthPct.toFloat() / total > 0.8f) return "力量训练占比偏高，可以适当增加有氧来平衡训练结构。"
    return "本周训练节奏不错，继续观察体重和力量的联动变化。"
}

private fun getBodyAdvice(uiState: StatsUiState): String {
    return when {
        uiState.latestFatigueScore != null && uiState.latestFatigueScore >= 8 ->
            "最近疲劳感偏高，下一次训练可以主动降一点容量，先把恢复拉回来。"
        uiState.latestSleepHours != null && uiState.latestSleepHours < 6f ->
            "最近睡眠偏少，先优先补睡眠，再观察体重和力量波动。"
        uiState.latestBodyFatPercentage != null && uiState.weightChangeSinceLastRecordKg != null &&
            uiState.weightChangeSinceLastRecordKg > 0.5f ->
            "体重近期有上升趋势，可以关注一下饮食和训练量的平衡。"
        else -> "身体指标整体稳定，继续坚持当前训练节奏。"
    }
}

private fun getStrengthAdvice(uiState: StatsUiState): String {
    val trend = uiState.strengthExerciseTrends[uiState.selectedExercise].orEmpty()
    if (trend.size >= 2) {
        val lastTwo = trend.takeLast(2)
        if (lastTwo[1].maxWeightKg < lastTwo[0].maxWeightKg) return "${uiState.selectedExercise} 重量在进步，继续按当前计划训练。"
        if (lastTwo[1].maxWeightKg == lastTwo[0].maxWeightKg) return "${uiState.selectedExercise} 重量保持稳定，可以考虑下一次尝试加重或加次数。"
    }
    return "继续记录力量训练，积累更多数据后会有更精准的建议。"
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:assembleDebug`

---

### Task 13: Tests

**Files:** `OfflineFitnessRepositoryTest.kt`

- [ ] **Step 1: Update `FakeStatsDao` with new methods**

```kotlin
private class FakeStatsDao : StatsDao {
    // existing fields...
    private val cardioByDate = MutableStateFlow(emptyList<CardioByDate>())
    private val cardioDuration = MutableStateFlow(emptyList<CardioDurationRow>())
    private val strengthTrend = MutableStateFlow(emptyList<StrengthTrendRow>())
    private val cardioMinutes = MutableStateFlow(0)
    private val bodyMetricTrend = MutableStateFlow(emptyList<BodyMetricTrend>())
    private val workoutDates = MutableStateFlow(emptyList<Long>())

    // existing method overrides...

    override fun observeCardioByDateRange(startDay: Long, endDay: Long) = cardioByDate
    override fun observeCardioDurationTrend(startDay: Long, endDay: Long) = cardioDuration
    override fun observeStrengthTrend(startDay: Long, endDay: Long) = strengthTrend
    override fun observeCardioMinutesBetween(startDay: Long, endDay: Long) = cardioMinutes
    override fun observeBodyMetricTrend(): Flow<List<BodyMetricTrend>> = bodyMetricTrend
    override fun observeWorkoutDates(): Flow<List<Long>> = workoutDates
}
```

- [ ] **Step 2: Also update `FakeWorkoutLogDao` with new methods**

```kotlin
private class FakeWorkoutLogDao(...) : WorkoutLogDao {
    // ... existing

    override fun observeWorkoutCountBetween(startDay: Long, endDay: Long): Flow<Int> {
        return MutableStateFlow(workouts.value.count {
            it.workoutLog.dateEpochDay in startDay..endDay
        })
    }

    override fun observeTrainingDaysBetween(startDay: Long, endDay: Long): Flow<Int> {
        val days = workouts.value.map { it.workoutLog.dateEpochDay }
            .filter { it in startDay..endDay }
            .distinct().size
        return MutableStateFlow(days)
    }
}
```

- [ ] **Step 3: Write test for week-over-week changes**

```kotlin
@Test
fun observe_week_over_week_changes_computes_deltas() = runBlocking {
    val today = LocalDate.now()
    val thisWeekStart = today.with(java.time.DayOfWeek.MONDAY).toEpochDay()
    val lastWeekStart = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY).toEpochDay()

    val workoutDao = FakeWorkoutLogDao(
        initialWorkouts = listOf(
            fakeWorkout(
                id = 1L,
                title = "本周训练",
                dateEpochDayOverride = thisWeekStart,
            ),
            fakeWorkout(
                id = 2L,
                title = "上周训练",
                dateEpochDayOverride = lastWeekStart,
            ),
        ),
    )
    val repository = createRepository(workoutDao = workoutDao)

    val wow = repository.observeWeekOverWeekChanges().first()

    assertEquals(1, wow.workoutCountChange)
    assertEquals(1, wow.previousWeekWorkoutCount)
}
```

Note: `fakeWorkout()` needs a `dateEpochDayOverride` parameter added:

```kotlin
private fun fakeWorkout(
    id: Long,
    title: String,
    notes: String,
    dateEpochDayOverride: Long? = null,
    exercises: List<StrengthExerciseWithSets> = listOf(
        fakeExercise(exerciseId = id * 10, workoutLogId = id, name = "卧推", sets = listOf(60f to 8)),
    ),
): WorkoutWithDetails {
    val epochDay = dateEpochDayOverride ?: 20_208L
    return WorkoutWithDetails(
        workoutLog = WorkoutLogEntity(
            id = id, dateEpochDay = epochDay, title = title, notes = notes, createdAtMillis = id,
        ),
        strengthExercises = exercises,
        cardioEntries = emptyList(),
    )
}
```

- [ ] **Step 4: Write test for training days**

```kotlin
@Test
fun observe_training_days_counts_distinct_days() = runBlocking {
    val dao = FakeWorkoutLogDao(
        initialWorkouts = listOf(
            fakeWorkout(id = 1L, title = "Day 1", dateEpochDayOverride = 100L),
            fakeWorkout(id = 2L, title = "Day 1 again", dateEpochDayOverride = 100L),
            fakeWorkout(id = 3L, title = "Day 2", dateEpochDayOverride = 101L),
        ),
    )
    val repository = createRepository(workoutDao = dao)
    val days = dao.observeTrainingDaysBetween(100L, 101L).first()
    assertEquals(2, days)
}
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`

---

### Self-Review Checklist

1. **Spec coverage:**
   - [ ] Overview row with 4 cards + sparklines → Task 7
   - [ ] Week-over-week comparison → Task 3 (wow) + Task 7 (UI)
   - [ ] Time range filter → Task 5 (state) + Task 8 (UI)
   - [ ] Tab navigation → Task 5 (state) + Task 8 (UI)
   - [ ] Tab 1: training frequency bars, donut distribution, cardio trend → Task 9
   - [ ] Tab 2: period comparison row, weight+bf% chart, mini trends → Task 10
   - [ ] Tab 3: exercise selector, stats row, weight/volume charts → Task 11
   - [ ] Advice card per tab → Task 12
   - [ ] Interaction: filter/tab switching, overview click → Task 7-8
   - [ ] No CUSTOM time range (YAGNI) → correctly omitted
   - [ ] No data export/share (YAGNI) → correctly omitted

2. **Placeholder scan:** All steps contain actual code or explicit signatures. No TBD/TODO.

3. **Type consistency:** `TimeRange` enum → used in ViewModel and UI. `WeekOverWeekChanges` data class → consistent field names across repository, ViewModel, UI.
