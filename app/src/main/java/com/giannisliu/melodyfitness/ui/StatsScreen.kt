package com.giannisliu.melodyfitness.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.giannisliu.melodyfitness.data.repository.BodyMetricTrendPoint
import com.giannisliu.melodyfitness.data.repository.CardioDurationPoint
import com.giannisliu.melodyfitness.data.repository.StrengthTrendPoint
import com.giannisliu.melodyfitness.data.repository.SparklineData
import com.giannisliu.melodyfitness.data.repository.WeekOverWeekChanges
import com.giannisliu.melodyfitness.data.repository.WeeklyTrainingComparison
import com.giannisliu.melodyfitness.data.repository.WeeklyWorkoutCount
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import com.giannisliu.melodyfitness.data.repository.WorkoutTypeCount
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.common.component.TextComponent

private enum class SparklineType { BAR, LINE }

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
            // Fixed header section (does NOT scroll)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OverviewRow(
                    weeklyWorkoutCount = uiState.weeklyWorkoutCount,
                    totalCardioMinutes = uiState.totalCardioMinutes,
                    latestWeightKg = uiState.latestWeightKg,
                    wow = uiState.weekOverWeekChanges,
                    onCardClick = { onUpdateActiveTab(it) },
                    sparklineData = uiState.sparklineData,
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

            // Scrollable content section
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
                        transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
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

// ── Overview Row ──────────────────────────────────────────────

@Composable
private fun OverviewRow(
    weeklyWorkoutCount: Int,
    totalCardioMinutes: Int,
    latestWeightKg: Float?,
    wow: WeekOverWeekChanges,
    onCardClick: (StatsTab) -> Unit,
    sparklineData: SparklineData,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                sparklineType = SparklineType.LINE,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                sparklineType = SparklineType.LINE,
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
                sparklineType = SparklineType.LINE,
            )
        }
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
    sparklineData: List<Float> = emptyList(),
    sparklineType: SparklineType = SparklineType.BAR,
    lineColor: Color = Color(0xFF1f6f50),
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
                        lineColor = lineColor,
                    )
                }
            }
        }
    }
}

private fun formatWeightChange(latestKg: Float, change: Float?, unit: WeightUnit): String {
    val formatted = formatWeight(latestKg, unit)
    if (change == null) return formatted
    val prefix = if (change >= 0) "+" else ""
    return "$prefix${formatFloat(change)} ${unit.symbol}"
}

// ── Time Range + Tab Navigation ──────────────────────────────

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

// ── Tab 1: Training Analysis ─────────────────────────────────

@Composable
private fun TrainingAnalysisTab(uiState: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WeeklyTrainingComparisonCard(
            comparisons = uiState.weeklyTrainingComparison,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrainingFrequencyBars(
                modifier = Modifier.weight(1f),
                weeklyCounts = uiState.weeklyWorkoutCounts,
            )
            WorkoutDistributionChart(
                modifier = Modifier.weight(1f),
                distribution = uiState.workoutTypeDistribution,
            )
        }
        CardioDurationChart(
            trend = uiState.cardioDurationTrend,
        )
    }
}

@Composable
private fun WeeklyTrainingComparisonCard(
    comparisons: List<WeeklyTrainingComparison>,
    modifier: Modifier = Modifier,
) {
    if (comparisons.isEmpty()) {
        EmptyChartCard(title = "近 8 周对比", message = "暂无训练数据")
        return
    }

    val activeWeeks = comparisons.count {
        it.workoutCount > 0 || it.trainingDays > 0 || it.cardioMinutes > 0
    }
    val latest = comparisons.lastOrNull()
    val previous = comparisons.getOrNull(comparisons.lastIndex - 1)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("近 8 周对比", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                latest?.let {
                    Text(
                        text = "最近 ${it.workoutCount} 次 / ${it.trainingDays} 天",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (latest != null && previous != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeeklyComparisonSummary(
                        modifier = Modifier.weight(1f),
                        label = "训练",
                        value = "${latest.workoutCount} 次",
                        previousValue = "上周 ${previous.workoutCount}",
                        change = latest.workoutCountChange,
                    )
                    WeeklyComparisonSummary(
                        modifier = Modifier.weight(1f),
                        label = "天数",
                        value = "${latest.trainingDays} 天",
                        previousValue = "上周 ${previous.trainingDays}",
                        change = latest.trainingDaysChange,
                    )
                    WeeklyComparisonSummary(
                        modifier = Modifier.weight(1f),
                        label = "有氧",
                        value = "${latest.cardioMinutes} 分",
                        previousValue = "上周 ${previous.cardioMinutes}",
                        change = latest.cardioMinutesChange,
                    )
                }
            }

            WeeklyComparisonBars(comparisons = comparisons)

            Text(
                text = "近 8 周活跃 $activeWeeks 周 · 合计 ${comparisons.sumOf { it.workoutCount }} 次训练 · ${comparisons.sumOf { it.cardioMinutes }} 分钟有氧",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WeeklyComparisonSummary(
    label: String,
    value: String,
    previousValue: String,
    change: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "${formatSignedChange(change)} · $previousValue",
            color = comparisonChangeColor(change),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun WeeklyComparisonBars(comparisons: List<WeeklyTrainingComparison>) {
    val maxWorkoutCount = comparisons.maxOfOrNull { it.workoutCount }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        comparisons.forEachIndexed { index, comparison ->
            val isLatest = index == comparisons.lastIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = comparison.workoutCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLatest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(((comparison.workoutCount.toFloat() / maxWorkoutCount) * 52).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (isLatest) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer,
                            ),
                    )
                }
                Text(
                    text = comparison.weekLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatSignedChange(change: Int?): String {
    if (change == null) return "-"
    if (change == 0) return "持平"
    val prefix = if (change > 0) "+" else ""
    return "$prefix$change"
}

@Composable
private fun comparisonChangeColor(change: Int?): Color {
    return when {
        change == null || change == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        change > 0 -> Color(0xFF1f6f50)
        else -> Color(0xFFc0392b)
    }
}

@Composable
private fun TrainingFrequencyBars(
    weeklyCounts: List<WeeklyWorkoutCount>,
    modifier: Modifier = Modifier,
) {
    if (weeklyCounts.isEmpty()) {
        EmptyChartCard(title = "训练频次", message = "暂无训练数据")
        return
    }
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
                    drawLine(
                        Color(0xFFb36a2c), Offset(0f, avgY), Offset(size.width, avgY),
                        strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                    )
                }
            }
            if (weeklyCounts.isNotEmpty()) {
                Text("周均 ${"%.1f".format(avg)} 次", color = Color(0xFFb36a2c), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WorkoutDistributionChart(
    distribution: List<WorkoutTypeCount>,
    modifier: Modifier = Modifier,
) {
    if (distribution.isEmpty()) {
        EmptyChartCard(title = "训练类型分布", message = "暂无训练数据")
        return
    }
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
                            drawArc(colors[index % colors.size], startAngle, sweep, useCenter = true)
                            startAngle += sweep
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    distribution.forEachIndexed { index, wc ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors[index % colors.size]),
                            )
                            Text("${wc.type} ${wc.count * 100 / total}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("共计 $total 次训练", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CardioDurationChart(
    trend: List<CardioDurationPoint>,
    modifier: Modifier = Modifier,
) {
    if (trend.isEmpty()) {
        EmptyChartCard(title = "有氧时长趋势", message = "暂无有氧数据")
        return
    }
    val totalMinutes = trend.sumOf { it.totalMinutes }
    val days = trend.size

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("有氧时长趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (trend.size < 2) return@Canvas
                    val maxMin = trend.maxOf { it.totalMinutes }.coerceAtLeast(1)
                    val points = trend.mapIndexed { i, p ->
                        val x = (i.toFloat() / (trend.size - 1)) * size.width
                        val y = (p.totalMinutes.toFloat() / maxMin) * size.height
                        Offset(x, size.height - y)
                    }

                    // Gradient area fill
                    val path = Path().apply {
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

// ── Tab 2: Body Metrics ───────────────────────────────────────

@Composable
private fun BodyMetricsTab(
    uiState: StatsUiState,
    weightUnit: WeightUnit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PeriodComparisonRow(uiState, weightUnit)

        EnhancedWeightTrendChart(
            trend = uiState.bodyMetricTrend,
            weightUnit = weightUnit,
        )

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

@Composable
private fun PeriodComparisonRow(
    uiState: StatsUiState,
    weightUnit: WeightUnit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ComparisonCard(modifier = Modifier.weight(1f), label = "体重", value = uiState.latestWeightKg?.let { formatWeight(it, weightUnit) } ?: "--", change = uiState.weightChangeSinceLastRecordKg)
        ComparisonCard(modifier = Modifier.weight(1f), label = "体脂", value = uiState.latestBodyFatPercentage?.let { "${formatFloat(it)}%" } ?: "--", change = null)
        ComparisonCard(modifier = Modifier.weight(1f), label = "腰围", value = uiState.latestWaistCm?.let { "${formatFloat(it)} cm" } ?: "--", change = null)
        ComparisonCard(modifier = Modifier.weight(1f), label = "睡眠", value = uiState.latestSleepHours?.let { "${formatFloat(it)} h" } ?: "--", change = null)
        ComparisonCard(modifier = Modifier.weight(1f), label = "疲劳", value = uiState.latestFatigueScore?.let { "$it/10" } ?: "--", change = uiState.latestFatigueScore?.let { -it.toFloat() })
    }
}

@Composable
private fun ComparisonCard(
    label: String,
    value: String,
    change: Float?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
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

@Composable
private fun EnhancedWeightTrendChart(
    trend: List<BodyMetricTrendPoint>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    if (trend.size < 2) {
        if (trend.isEmpty()) {
            EmptyChartCard(title = "体重趋势 + 体脂", message = "暂无身体指标数据")
            return
        }
        EmptyChartCard(title = "体重趋势 + 体脂", message = "至少需要 2 条记录")
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trend, weightUnit) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    trend.map { point ->
                        when (weightUnit) {
                            WeightUnit.KG -> point.weightKg.toDouble()
                            WeightUnit.LB -> (point.weightKg * 2.20462).toDouble()
                        }
                    },
                )
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
            val marker = rememberDefaultCartesianMarker(label = TextComponent())
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● 体重", color = Color(0xFF1f6f50), style = MaterialTheme.typography.labelSmall)
                if (trend.any { it.bodyFatPercentage != null }) {
                    Text("- - 体脂", color = Color(0xFFb36a2c), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

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
                        for (i in 0 until points.size - 1) {
                            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 2f)
                        }
                        referenceLine?.let { ref ->
                            val refY = ((ref - min) / range).toFloat() * size.height
                            drawLine(
                                Color(0xFFb36a2c), Offset(0f, size.height - refY),
                                Offset(size.width, size.height - refY), strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tab 3: Strength Progress ─────────────────────────────────

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
        ExerciseSelector(
            exercises = uiState.strengthExerciseNames,
            selected = uiState.selectedExercise,
            onSelect = onSelectExercise,
        )

        val selectedTrend = uiState.strengthExerciseTrends[uiState.selectedExercise].orEmpty()
        StrengthStatsRow(trend = selectedTrend)

        if (selectedTrend.size >= 2) {
            StrengthWeightChart(trend = selectedTrend)
            StrengthVolumeChart(trend = selectedTrend)
        } else {
            Text("选中动作的数据不足，暂无法展示趋势", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrengthStatsRow(trend: List<StrengthTrendPoint>) {
    val bestWeight = trend.maxOfOrNull { it.maxWeightKg } ?: 0f
    val lastWeight = trend.lastOrNull()?.maxWeightKg ?: 0f
    val prevWeight = trend.getOrNull(trend.size - 2)?.maxWeightKg
    val totalVolume = trend.sumOf { it.volumeKg.toDouble() }.toFloat()
    val lastVolume = trend.lastOrNull()?.volumeKg ?: 0f

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStatCard(modifier = Modifier.weight(1f), label = "最佳重量", value = "${formatFloat(bestWeight)} kg")
        MiniStatCard(modifier = Modifier.weight(1f), label = "本次重量", value = "${formatFloat(lastWeight)} kg", change = prevWeight?.let { lastWeight - it })
        MiniStatCard(modifier = Modifier.weight(1f), label = "总容量", value = "${formatFloat(totalVolume)} kg")
        MiniStatCard(modifier = Modifier.weight(1f), label = "本次容量", value = "${formatFloat(lastVolume)} kg")
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    change: Float? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

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
            val marker = rememberDefaultCartesianMarker(label = TextComponent())
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

// ── Advice Card ───────────────────────────────────────────────

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
        if (lastTwo[1].maxWeightKg > lastTwo[0].maxWeightKg) return "${uiState.selectedExercise} 重量在进步，继续按当前计划训练。"
        if (lastTwo[1].maxWeightKg == lastTwo[0].maxWeightKg) return "${uiState.selectedExercise} 重量保持稳定，可以考虑下一次尝试加重或加次数。"
    }
    return "继续记录力量训练，积累更多数据后会有更精准的建议。"
}

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

// ── Skeleton / Loading States ─────────────────────────────────

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
