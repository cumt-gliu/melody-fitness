# 统计页视觉打磨 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the stats page with sparklines, transition animations, chart enhancements, skeleton loading states, and sticky header.

**Architecture:** All UI changes in `StatsScreen.kt`. Sparkline data computed in repository layer from existing DAO flows. ViewModel adds one new combine source for sparkline data and a `isReady` flag. Charts enhanced via Vico API where possible (`areaFill`, `marker`) and Canvas overlay for PR markers.

**Tech Stack:** Compose UI, Vico 2.x charts, Compose Canvas, Room + coroutines

---

### File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `data/repository/FitnessRepository.kt` | Modify | Add `SparklineData` model + `observeSparklineData()` interface method |
| `data/repository/OfflineFitnessRepository.kt` | Modify | Implement `observeSparklineData()` — weekly aggregation from existing flows |
| `ui/FitnessViewModel.kt` | Modify | Add sparkline data to combine, `isReady` flag |
| `ui/StatsScreen.kt` | Modify | All UI: sparklines, transitions, chart enhancements, skeletons, sticky header |
| `data/repository/OfflineFitnessRepositoryTest.kt` | Modify | Add sparkline data tests in FakeStatsDao |

---

### Task 1: Sparkline — Data Model + Repository

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/data/repository/FitnessRepository.kt` (add model + interface method)
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/data/repository/OfflineFitnessRepository.kt` (implement)

**Step 1: Add SparklineData model to FitnessRepository.kt**

Append after `WeeklyWorkoutCount` (around line 180):

```kotlin
data class SparklineData(
    val weeklyWorkoutCounts: List<Int> = emptyList(),
    val weeklyCardioMinutes: List<Int> = emptyList(),
    val weeklyTrainingDays: List<Int> = emptyList(),
    val weeklyWeights: List<Float> = emptyList(),
)
```

**Step 2: Add `observeSparklineData()` to FitnessRepository interface**

Between `fun observeWeeklyWorkoutCounts(weeks: Int)` and `fun observeWeekOverWeekChanges()`:

```kotlin
fun observeSparklineData(): Flow<SparklineData>
```

**Step 3: Implement in OfflineFitnessRepository.kt**

Add after `observeWeeklyWorkoutCounts` (around line 220):

```kotlin
override fun observeSparklineData(): Flow<SparklineData> {
    val weekField = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()
    val today = LocalDate.now()
    val cutoff = today.minusWeeks(8).toEpochDay()

    return combine(
        observeWeeklyWorkoutCounts(weeks = 8),
        workoutLogDao.observeWorkoutLogs(),
        statsDao.observeWorkoutDates(),
        statsDao.observeBodyMetricTrend(),
    ) { weeklyCounts, workouts, dates, bodyTrend ->
        // weekly cardio minutes from workout logs
        val cardioByWeek = mutableMapOf<Int, Int>()
        workouts.filter { it.workoutLog.dateEpochDay >= cutoff }.forEach { w ->
            if (w.cardioEntries.isNotEmpty()) {
                val date = LocalDate.ofEpochDay(w.workoutLog.dateEpochDay)
                val yw = date.year * 100 + date.get(weekField)
                val mins = w.cardioEntries.sumOf { it.durationMinutes }
                cardioByWeek[yw] = (cardioByWeek[yw] ?: 0) + mins
            }
        }

        // weekly training days from dates
        val daysByWeek = mutableMapOf<Int, MutableSet<Long>>()
        dates.filter { it >= cutoff }.forEach { day ->
            val yw = LocalDate.ofEpochDay(day).let { it.year * 100 + it.get(weekField) }
            daysByWeek.getOrPut(yw) { mutableSetOf() }.add(day)
        }

        // weekly weights (latest per week) from body trend
        val weightByWeek = mutableMapOf<Int, Float>()
        bodyTrend.filter { it.dateEpochDay >= cutoff }.forEach { point ->
            val yw = LocalDate.ofEpochDay(point.dateEpochDay).let {
                it.year * 100 + it.get(weekField)
            }
            weightByWeek[yw] = point.weightKg
        }

        // align to 8-week window
        val weeks = (0 until 8).map { weekAgo ->
            today.minusWeeks(weekAgo.toLong()).let {
                it.year * 100 + it.get(weekField)
            }
        }.reversed()

        SparklineData(
            weeklyWorkoutCounts = weeklyCounts.map { it.count },
            weeklyCardioMinutes = weeks.map { cardioByWeek[it] ?: 0 },
            weeklyTrainingDays = weeks.map { daysByWeek[it]?.size ?: 0 },
            weeklyWeights = weeks.mapNotNull { weightByWeek[it] },
        )
    }
}
```

**Step 4: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 5: Commit**

```bash
git add -A
git commit -m "feat: add sparkline data model and repository method"
```

---

### Task 2: Sparkline — ViewModel Wiring

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/ui/FitnessViewModel.kt`

**Step 1: Update StatsUiState with sparkline + loading flag**

In `StatsUiState` (around line 108), add after `val selectedExercise: String = ""`:

```kotlin
    // Visual polish
    val sparklineData: SparklineData = SparklineData(),
    val isReady: Boolean = false,
```

**Step 2: Add sparkline flow + update combine in FitnessViewModel**

After `private val tabStrengthData` (around line 194), add:

```kotlin
private val sparklineData: StateFlow<SparklineData> = repository.observeSparklineData()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SparklineData())
```

Update `statsUiState` combine (around line 196) — add `sparklineData` as a fifth combine source:

```kotlin
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
```

**Step 3: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: wire sparkline data and ready flag into ViewModel"
```

---

### Task 3: Sparkline — Overview Card Canvas Rendering

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/ui/StatsScreen.kt`

**Step 1: Add SparklineType enum and sparkline Canvas composable**

After the existing `import` blocks and before `StatsScreen` composable, add:

```kotlin
private enum class SparklineType { BAR, LINE }
```

Then at the very end of the file (before the closing), add:

```kotlin
@Composable
private fun SparklineBar(
    data: List<Int>,
    modifier: Modifier = Modifier,
) {
    if (data.size < 2) return
    val avg = data.average()
    val max = data.max().coerceAtLeast(1)
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / data.size * 0.6f
            val gap = size.width / data.size * 0.4f
            data.forEachIndexed { i, v ->
                val h = (v.toFloat() / max) * size.height
                val x = i * (barWidth + gap) + gap / 2
                val color = if (v >= avg) Color(0xFF1f6f50) else Color(0xFFcc9a62)
                drawRect(color, Offset(x, size.height - h), Size(barWidth, h))
            }
        }
    }
}

@Composable
private fun SparklineLine(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF1f6f50),
) {
    if (data.size < 2) return
    val min = data.min()
    val max = data.max()
    val range = (max - min).coerceAtLeast(1f)
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val points = data.mapIndexed { i, v ->
                val x = (i.toFloat() / (data.size - 1)) * size.width
                val y = ((v - min) / range) * size.height
                Offset(x, size.height - y)
            }
            for (i in 0 until points.size - 1) {
                drawLine(lineColor, points[i], points[i + 1], strokeWidth = 1.5f)
            }
        }
    }
}
```

**Step 2: Update OverviewCard to accept sparkline data**

Replace the `OverviewCard` composable (around line 152-180):

```kotlin
@Composable
private fun OverviewCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    change: Int?,
    compareLabel: String,
    onClick: () -> Unit,
    sparklineData: List<Float> = emptyList(),
    sparklineType: SparklineType = SparklineType.LINE,
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
            // Sparkline
            if (sparklineData.size >= 2) {
                when (sparklineType) {
                    SparklineType.BAR -> SparklineBar(
                        data = sparklineData.map { it.toInt() },
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                    )
                    SparklineType.LINE -> SparklineLine(
                        data = sparklineData,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                    )
                }
            }
        }
    }
}
```

**Step 3: Update OverviewRow to pass sparkline data**

Replace the `OverviewRow` composable (around line 106-150):

```kotlin
@Composable
private fun OverviewRow(
    weeklyWorkoutCount: Int,
    totalCardioMinutes: Int,
    latestWeightKg: Float?,
    wow: WeekOverWeekChanges,
    sparklineData: SparklineData,
    onCardClick: (StatsTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "本周训练",
            value = "$weeklyWorkoutCount 次",
            change = wow.workoutCountChange,
            compareLabel = "vs 上周 ${wow.previousWeekWorkoutCount}",
            onClick = { onCardClick(StatsTab.TRAINING) },
            sparklineData = sparklineData.weeklyWorkoutCounts.map { it.toFloat() },
            sparklineType = SparklineType.BAR,
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "有氧时长",
            value = "${totalCardioMinutes}分钟",
            change = wow.cardioMinutesChange,
            compareLabel = "vs 上周 ${wow.previousWeekCardioMinutes}",
            onClick = { onCardClick(StatsTab.TRAINING) },
            sparklineData = sparklineData.weeklyCardioMinutes.map { it.toFloat() },
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "体重变化",
            value = latestWeightKg?.let {
                formatWeightChange(it, wow.weightChange, WeightUnit.KG)
            } ?: "--",
            change = null,
            compareLabel = "较上周",
            onClick = { onCardClick(StatsTab.BODY) },
            sparklineData = sparklineData.weeklyWeights,
            lineColor = Color(0xFFb36a2c),
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = "训练天数",
            value = "${wow.previousWeekTrainingDays + (wow.trainingDaysChange ?: 0)} 天",
            change = wow.trainingDaysChange,
            compareLabel = "vs 上周 ${wow.previousWeekTrainingDays}",
            onClick = { onCardClick(StatsTab.TRAINING) },
            sparklineData = sparklineData.weeklyTrainingDays.map { it.toFloat() },
        )
    }
}
```

Note: `OverviewCard` now needs an optional `lineColor` parameter. Add it:

```kotlin
    lineColor: Color = Color(0xFF1f6f50),
```

And pass it to `SparklineLine`:

```kotlin
    SparklineType.LINE -> SparklineLine(
        data = sparklineData,
        modifier = Modifier.fillMaxWidth().height(20.dp),
        lineColor = lineColor,
    )
```

**Step 4: Update the StatsScreen call site to pass sparklineData**

In `StatsScreen` composable, update the `OverviewRow` call:

```kotlin
OverviewRow(
    weeklyWorkoutCount = uiState.weeklyWorkoutCount,
    totalCardioMinutes = uiState.totalCardioMinutes,
    latestWeightKg = uiState.latestWeightKg,
    wow = uiState.weekOverWeekChanges,
    sparklineData = uiState.sparklineData,
    onCardClick = { onUpdateActiveTab(it) },
)
```

**Step 5: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 6: Commit**

```bash
git add -A
git commit -m "feat: add sparkline trend lines to overview cards"
```

---

### Task 4: Transition Animations for Tab and Time Range Switching

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/ui/StatsScreen.kt`

**Step 1: Wrap tab content with AnimatedContent (fade)**

In `StatsScreen`, replace the `when (uiState.activeTab) { ... }` block with:

```kotlin
AnimatedContent(
    targetState = uiState.activeTab,
    transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
    label = "tab-content",
) { tab ->
    when (tab) {
        StatsTab.TRAINING -> TrainingAnalysisTab(uiState)
        StatsTab.BODY -> BodyMetricsTab(uiState, weightUnit)
        StatsTab.STRENGTH -> StrengthProgressTab(uiState, onSelectExercise)
    }
}
```

Add imports at the top of the file:

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
```

**Step 2: Wrap time-range-dependent content with Crossfade**

Alternatively, wrap the entire content area (below tabs) with Crossfade keyed on `selectedTimeRange`. Since the time range change triggers a data reload, the Crossfade should wrap the `AnimatedContent`:

```kotlin
Crossfade(
    targetState = uiState.selectedTimeRange,
    animationSpec = tween(300),
    label = "range-crossfade",
) {
    AnimatedContent(
        targetState = uiState.activeTab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tab-content",
    ) { tab ->
        when (tab) { ... }
    }
}
```

Import:

```kotlin
import androidx.compose.animation.Crossfade
```

**Step 3: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: add fade transition animations for tab and time range switching"
```

---

### Task 5: Chart Visual Enhancements

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/ui/StatsScreen.kt`

**Step 1: Gradient area fill on CardioDurationChart**

Replace the `CartesianChartHost` call in `CardioDurationChart` (around line 353-361):

```kotlin
CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            areaFill = LineCartesianLayer.AreaFill.single(
                Color(0xFF1f6f50).copy(alpha = 0.12f),
            ),
        ),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
    ),
    modelProducer = modelProducer,
    modifier = Modifier.fillMaxWidth().height(140.dp),
)
```

Add imports:

```kotlin
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
```

If `LineCartesianLayer.AreaFill.single` does not compile (API varies by Vico version), use a Canvas-based approach instead: override the chart with a custom composable that draws the line + gradient area below it.

Actually, to be safe, implement the gradient fill via the Canvas approach that we already use for other charts. Replace the Vico line chart with a Canvas-based implementation:

```kotlin
@Composable
private fun CardioDurationChart(
    trend: List<CardioDurationPoint>,
    modifier: Modifier = Modifier,
) {
    if (trend.isEmpty()) return
    val totalMinutes = trend.sumOf { it.totalMinutes }
    val days = trend.size

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("有氧时长趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val points = trend.mapIndexed { i, p ->
                        val x = (i.toFloat() / (trend.size - 1).coerceAtLeast(1)) * size.width
                        val maxMin = trend.maxOf { it.totalMinutes }.coerceAtLeast(1)
                        val y = (p.totalMinutes.toFloat() / maxMin) * size.height
                        Offset(x, size.height - y)
                    }

                    // Gradient area fill
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, size.height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, size.height)
                        close()
                    }
                    drawPath(
                        path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1f6f50).copy(alpha = 0.15f),
                                Color(0xFF1f6f50).copy(alpha = 0.02f),
                            ),
                            endY = size.height,
                        ),
                    )

                    // Line
                    for (i in 0 until points.size - 1) {
                        drawLine(Color(0xFF1f6f50), points[i], points[i + 1], strokeWidth = 2f)
                    }
                }
            }
            Text("本期累计 ${totalMinutes}分钟 · 日均 ${totalMinutes / days}分钟",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

Add import:

```kotlin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
```

**Step 2: Tooltip markers on line charts**

For `CardioDurationChart`, `EnhancedWeightTrendChart`, and `StrengthWeightChart` — add Vico marker support. Since we switched `CardioDurationChart` to Canvas (Step 1), apply tooltips to the Vico-based charts.

For `EnhancedWeightTrendChart` (around line 481-501), add `marker`:

```kotlin
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.common.Marker

val marker = rememberMarker()

CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
        marker = marker,
    ),
    modelProducer = modelProducer,
    modifier = Modifier.fillMaxWidth().height(180.dp),
)
```

For `StrengthWeightChart` (around line 647-665), add the same:

```kotlin
val marker = rememberMarker()

CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
        marker = marker,
    ),
    modelProducer = modelProducer,
    modifier = Modifier.fillMaxWidth().height(160.dp),
)
```

Add the marker import at the top:

```kotlin
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.common.Marker
import com.patrykandpatrick.vico.compose.common.rememberMarker
```

**Step 3: PR markers on StrengthWeightChart**

After the Vico `CartesianChartHost` in `StrengthWeightChart`, add a Canvas overlay for PR markers. Wrap the chart + overlay in a `Box`:

Replace the current CartesianChartHost in `StrengthWeightChart`:

```kotlin
Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
            marker = marker,
        ),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxSize(),
    )

    // PR marker overlay
    if (prIndices.isNotEmpty() && trend.size >= 2) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val min = trend.minOf { it.maxWeightKg }
            val max = trend.maxOf { it.maxWeightKg }
            val range = (max - min).coerceAtLeast(1f)
            prIndices.forEach { idx ->
                val x = (idx.toFloat() / (trend.size - 1)) * size.width
                val y = ((trend[idx].maxWeightKg - min) / range) * size.height
                val py = size.height - y
                drawCircle(Color(0xFFb36a2c), radius = 5f, center = Offset(x, py))
                drawCircle(Color.White, radius = 2.5f, center = Offset(x, py))
            }
        }
    }
}
```

**Step 4: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 5: Commit**

```bash
git add -A
git commit -m "feat: enhance charts with gradient fill, tooltips, and PR markers"
```

---

### Task 6: Skeleton / Loading States

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/ui/StatsScreen.kt`

**Step 1: Add ShimmerPlaceholder composable**

At the end of the file, before the closing, add:

```kotlin
@Composable
private fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-alpha",
    )
    Box(
        modifier = modifier.background(Color.LightGray.copy(alpha = alpha), shape),
    )
}

@Composable
private fun SkeletonCard(
    modifier: Modifier = Modifier,
    chartHeight: Int = 120,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth(0.5f).height(14.dp),
                shape = RoundedCornerShape(2.dp),
            )
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(chartHeight.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }
    }
}
```

Add imports:

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Shape
```

**Step 2: Add empty state helper composable**

Add:

```kotlin
@Composable
private fun EmptyChartCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

**Step 3: Add `isReady` guard to StatsScreen**

At the top of the `StatsScreen` body, after `Column {`, wrap the entire content with `if (uiState.isReady)` and show a skeleton when not ready:

```kotlin
@Composable
fun StatsScreen(
    paddingValues: PaddingValues,
    uiState: StatsUiState,
    weightUnit: WeightUnit,
    onUpdateTimeRange: (TimeRange) -> Unit = {},
    onUpdateActiveTab: (StatsTab) -> Unit = {},
    onSelectExercise: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(title = "训练统计")

        if (!uiState.isReady) {
            // Loading skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) {
                    SkeletonCard(modifier = Modifier.weight(1f), chartHeight = 20)
                }
            }
            SkeletonCard(chartHeight = 60)
            SkeletonCard(chartHeight = 80)
        } else {
            // Real content (existing code)
            OverviewRow(...)
            TimeRangeFilter(...)
            StatsTabRow(...)
            AnimatedContent(...) { ... }
            StatsAdviceCard(uiState)
        }
    }
}
```

**Step 4: Update empty state in each chart composable**

Replace silent `return` in `TrainingFrequencyBars` (around line 250):

```kotlin
if (weeklyCounts.isEmpty()) {
    EmptyChartCard(title = "训练频次", message = "暂无训练数据")
    return
}
```

Replace silent `return` in `WorkoutDistributionChart` (around line 292):

```kotlin
if (distribution.isEmpty()) {
    EmptyChartCard(title = "训练类型分布", message = "暂无训练数据")
    return
}
```

Replace silent `return` in `CardioDurationChart` (around line 337):

```kotlin
if (trend.isEmpty()) {
    EmptyChartCard(title = "有氧时长趋势", message = "暂无有氧数据")
    return
}
```

Replace the early return in `EnhancedWeightTrendChart` (around line 450-458):

```kotlin
if (trend.size < 2) {
    if (trend.isEmpty()) {
        EmptyChartCard(title = "体重趋势 + 体脂", message = "暂无身体指标数据")
        return
    }
    // Show minimalist card for single data point
    EmptyChartCard(title = "体重趋势 + 体脂", message = "至少需要 2 条记录")
    return
}
```

**Step 5: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 6: Commit**

```bash
git add -A
git commit -m "feat: add skeleton loading states and empty chart placeholders"
```

---

### Task 7: Sticky Header — Layout Restructuring

**Files:**
- Modify: `app/src/main/java/com/giannisliu/melodyfitness/ui/StatsScreen.kt`

**Step 1: Split the single-scroll Column into fixed header + scrollable content**

Replace the entire `StatsScreen` composable:

```kotlin
@Composable
fun StatsScreen(
    paddingValues: PaddingValues,
    uiState: StatsUiState,
    weightUnit: WeightUnit,
    onUpdateTimeRange: (TimeRange) -> Unit = {},
    onUpdateActiveTab: (StatsTab) -> Unit = {},
    onSelectExercise: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SectionTitle(title = "训练统计")

        if (!uiState.isReady) {
            // Loading skeleton (scrollable to avoid overflow)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) {
                        SkeletonCard(modifier = Modifier.weight(1f), chartHeight = 20)
                    }
                }
                SkeletonCard(chartHeight = 60)
                SkeletonCard(chartHeight = 80)
            }
        } else {
            // Fixed header section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OverviewRow(
                    weeklyWorkoutCount = uiState.weeklyWorkoutCount,
                    totalCardioMinutes = uiState.totalCardioMinutes,
                    latestWeightKg = uiState.latestWeightKg,
                    wow = uiState.weekOverWeekChanges,
                    sparklineData = uiState.sparklineData,
                    onCardClick = { onUpdateActiveTab(it) },
                )
                TimeRangeFilter(
                    selected = uiState.selectedTimeRange,
                    onSelect = onUpdateTimeRange,
                )
                StatsTabRow(
                    selected = uiState.activeTab,
                    onSelect = onUpdateActiveTab,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Crossfade(
                    targetState = uiState.selectedTimeRange,
                    animationSpec = tween(300),
                    label = "range-crossfade",
                ) {
                    AnimatedContent(
                        targetState = uiState.activeTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tab-content",
                    ) { tab ->
                        when (tab) {
                            StatsTab.TRAINING -> TrainingAnalysisTab(uiState)
                            StatsTab.BODY -> BodyMetricsTab(uiState, weightUnit)
                            StatsTab.STRENGTH -> StrengthProgressTab(uiState, onSelectExercise)
                        }
                    }
                }
                StatsAdviceCard(uiState)
            }
        }
    }
}
```

**Step 2: Remove `verticalScroll` from the outer Column padding**

Note that unlike the current code, the outer `Column` does NOT have `.verticalScroll()`. Instead:
- Fixed header has no scroll
- Content has `.weight(1f).verticalScroll(rememberScrollState())`

Make sure `rememberScrollState()` is imported (it's already used in the existing code).

**Step 3: Verify compile**

Run: `./gradlew :app:assembleDebug`

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: restructure layout with sticky header and scrollable content"
```

---

### Task 8: Tests

**Files:**
- Modify: `app/src/test/java/com/giannisliu/melodyfitness/data/repository/OfflineFitnessRepositoryTest.kt`

**Step 1: Update FakeStatsDao with methods needed by sparkline computation**

Add to the existing `FakeStatsDao` class:

```kotlin
override fun observeBodyMetricTrend(): Flow<List<BodyMetricTrend>> {
    return bodyMetricTrend
}
```

And add the field at the top:

```kotlin
private val bodyMetricTrend = MutableStateFlow(emptyList<BodyMetricTrend>())
```

(Note: `observeBodyMetricTrend()` may already be defined in the fake — check and add if missing.)

**Step 2: Update FakeWorkoutLogDao to support sparkline data queries**

Ensure `observeWorkoutLogs()` returns the right data structure. The fake should already have this. Add a test helper method to seed cardio data on workout logs:

```kotlin
private fun fakeWorkoutWithCardio(
    id: Long,
    dateEpochDay: Long,
    cardioMinutes: Int,
): WorkoutWithDetails {
    return WorkoutWithDetails(
        workoutLog = WorkoutLogEntity(
            id = id, dateEpochDay = dateEpochDay,
            title = "Cardio Workout $id", notes = "", createdAtMillis = id,
        ),
        strengthExercises = emptyList(),
        cardioEntries = listOf(
            CardioEntryEntity(
                id = id * 100, workoutLogId = id,
                activityType = "跑步", durationMinutes = cardioMinutes,
                distanceKm = 0f, averagePace = "", notes = "",
            ),
        ),
    )
}
```

**Step 3: Write sparkline data test**

```kotlin
@Test
fun observe_sparkline_data_computes_weekly_values() = runBlocking {
    val today = LocalDate.now()
    val thisWeek = today.with(DayOfWeek.MONDAY).toEpochDay()
    val lastWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY).toEpochDay()

    val workoutDao = FakeWorkoutLogDao(
        initialWorkouts = listOf(
            fakeWorkoutWithCardio(id = 1L, dateEpochDay = thisWeek, cardioMinutes = 30),
            fakeWorkoutWithCardio(id = 2L, dateEpochDay = lastWeekStart, cardioMinutes = 45),
        ),
    )
    val bodyMetricDao = FakeBodyMetricDao(
        initialMetrics = listOf(
            BodyMetricEntity(
                dateEpochDay = thisWeek, weightKg = 72f,
                bodyFatPercentage = null, waistCm = null,
                sleepHours = null, fatigueScore = null, notes = "",
            ),
        ),
    )
    val repository = createRepository(workoutDao = workoutDao, bodyMetricDao = bodyMetricDao)
    val sparkline = repository.observeSparklineData().first()

    assertTrue(sparkline.weeklyCardioMinutes.any { it > 0 }, "Should have cardio minutes")
    assertTrue(sparkline.weeklyWeights.isNotEmpty(), "Should have weight data")
    assertEquals(8, sparkline.weeklyWorkoutCounts.size, "Should have 8 weeks of data")
}
```

**Step 4: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 5: Commit**

```bash
git add -A
git commit -m "test: add tests for sparkline data computation"
```

---

### Self-Review Checklist

1. **Spec coverage:**
   - [ ] Overview sparklines with 4 data sources → Task 1 (data) + Task 2 (VM) + Task 3 (UI)
   - [ ] Fade transition for tab switching → Task 4
   - [ ] Crossfade for time range switching → Task 4
   - [ ] Gradient area fill on cardio chart → Task 5 (Step 1)
   - [ ] Vico marker tooltips → Task 5 (Step 2)
   - [ ] PR markers on strength weight chart → Task 5 (Step 3)
   - [ ] Shimmer skeleton loading state → Task 6 (Step 1-3)
   - [ ] Empty state placeholders for each chart → Task 6 (Step 4)
   - [ ] Sticky header layout → Task 7
   - [ ] Tests → Task 8

2. **Placeholder scan:** All steps contain actual code or explicit signatures. No TBD/TODO.

3. **Type consistency:** `SparklineData` model used consistently across repository → ViewModel → UI. `SparklineType` enum used in OverviewCard. `isReady` flag consistent between ViewModel and StatsScreen.
