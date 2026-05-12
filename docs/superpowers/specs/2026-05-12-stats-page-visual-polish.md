# 统计页视觉打磨

## 概述

在已完成的三层布局统计页基础上，进行一轮视觉和交互打磨。涵盖 5 个方向：概览行 sparkline、切换动效、图表视觉增强、骨架屏/空状态、粘性头部。

## 1. 概览行 Sparkline 迷你趋势线

### 概览

四张概览卡片底部各增加一条 8 周迷你趋势线，让概览从"只看当期数值"提升到"一眼看到变化趋势"。

### 卡片与数据源

| 卡片 | Sparkline 类型 | 数据源 | 当前状态 |
|------|---------------|--------|---------|
| 本周训练 | 迷你柱状图 | `weeklyWorkoutCounts`（已有） | 已有 |
| 有氧时长 | 迷你折线图 | 每周 cardio 分钟合计 | 需新增 DAO 查询 |
| 体重变化 | 迷你折线图 | `bodyMetricTrend` 过滤近 8 周 | 已有，需聚合到周 |
| 训练天数 | 迷你折线图 | 每周 DISTINCT 训练天数 | 需新增 DAO 查询 |

### 新增数据模型和 Repository 方法

Sparkline 数据在 Repository 层聚合（与现有 `observeWeeklyWorkoutCounts` 模式一致），不需要新增 DAO 查询即可从已有的原始数据推导。

**数据模型：**

```kotlin
data class SparklineData(
    val weeklyWorkoutCounts: List<Int> = emptyList(),
    val weeklyCardioMinutes: List<Int> = emptyList(),
    val weeklyTrainingDays: List<Int> = emptyList(),
    val weeklyWeights: List<Float> = emptyList(),
)
```

**FitnessRepository 新增方法：**

```kotlin
fun observeSparklineData(): Flow<SparklineData>
```

**OfflineFitnessRepository 实现说明：**

- `weeklyWorkoutCounts` — 复用现有 `observeWeeklyWorkoutCounts(weeks = 8)` 的返回值
- `weeklyCardioMinutes` — 从 `workoutLogDao.observeWorkoutLogs()` + 关联 cardio 条目按周聚合
- `weeklyTrainingDays` — 从 `observeWorkoutDates()` 相同原始数据按周统计 DISTINCT 天数
- `weeklyWeights` — 从 `bodyMetricTrend` 取每周最后一条记录的体重值

周对齐方式：统一使用 `java.time.temporal.WeekFields` 计算 ISO 周号，与现有实现一致。

**数据模型：**

```kotlin
data class SparklineData(
    val weeklyWorkoutCounts: List<Int> = emptyList(),   // 已有
    val weeklyCardioMinutes: List<Int> = emptyList(),    // 新增
    val weeklyTrainingDays: List<Int> = emptyList(),     // 新增
    val weeklyWeights: List<Float> = emptyList(),        // 从 bodyMetricTrend 聚合
)
```

### UI 实现

使用 Compose Canvas 手绘，Lightweight（每根 sparkline ~20 行 Canvas 代码）：

- **柱状 sparkline**（训练次数）：`drawRect` 逐周绘制，颜色规则同柱状图（≥ 周均 = `#1f6f50`，< 周均 = `#cc9a62`）
- **折线 sparkline**（有氧/体重/训练天数）：`drawLine` 连接各周数据点
- 高度：24dp，固定在卡片底部

## 2. 切换动效

### Tab 切换

使用 Compose `AnimatedContent` 实现淡入淡出（用户选择方案 A）：

```kotlin
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
```

### 时间范围切换

使用 `Crossfade` 包裹整个 Tab 内容区，当 `selectedTimeRange` 变化时平滑过渡。

### 要点

- 动效仅在 Tab / 时间范围切换时触发，不影响页面初次加载
- 配合骨架屏使用：首次加载显示骨架屏，后续切换显示淡入淡出动效

## 3. 图表视觉增强

### 3.1 有氧趋势面积渐变填充

当前有氧趋势为纯折线。增加从主色 `#1f6f50`（opacity 0.15）到透明（opacity 0.02）的渐变填充区域。

实现方式：Vico `rememberLineCartesianLayer(areaFill = ...)`。若 Vico API 不支持直接设置 areaFill，使用 Compose Canvas 包裹替代。

### 3.2 力量重量 PR 标记点

当前只在底部文字标注 PR 值。改为在折线图上标记橙色圆点 + "PR N kg" 标签。

PR 检测逻辑不变（数据点是到该点为止的最大值即为 PR）。

实现方式：Vico `rememberLineCartesianLayer` 结合自定义 `pointProvider`，或在 Vico chart 上方叠加 Canvas 层绘制标记点。

### 3.3 Tooltip 数据点提示

使用 Vico 内置 Marker 功能：

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
    modifier = Modifier.fillMaxWidth().height(140.dp),
)
```

适用图表：
- 有氧时长趋势折线图
- 体重 + 体脂叠加趋势图
- 力量重量趋势折线图

提示内容：日期 + 数值（Vico 默认通过 `rememberMarker()` 提供）。

## 4. 骨架屏 / 加载态 / 空状态

### 状态定义

`StatsUiState` 新增字段：

```kotlin
val isOverviewReady: Boolean = false,
val isTabDataReady: Boolean = false,
```

### 骨架屏组件

无额外依赖。使用 Compose `animateFloatAsState` + Canvas 绘制 shimmer 动画：

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
        modifier = modifier
            .background(Color.LightGray.copy(alpha = alpha), shape),
    )
}
```

### 各组件状态转换

| 组件 | 无数据时 | 加载中 |
|------|---------|--------|
| 训练频次柱状图 | "暂无训练数据" 占位卡 | 柱状图骨架 |
| 类型分布环形图 | "暂无训练数据" 占位卡 | 环形图骨架 |
| 有氧时长趋势 | "暂无有氧数据" 占位卡 | 折线图骨架 |
| 体重趋势图 | "至少需要 2 条记录"（优化卡片样式） | 折线图骨架 |
| 迷你趋势图 | 灰色占位框 | 迷你折线骨架 |
| 力量 Tab | "还没有力量训练记录"（已有） | 骨架卡片 |

## 5. 粘性头部

### 布局变更

将当前单一 `Column` + `verticalScroll` 拆分为两层布局：

```
Scaffold
  └─ Column
       ├─ [固定头部] Column (fillMaxWidth, 不滚动)
       │    ├─ OverviewRow
       │    ├─ TimeRangeFilter
       │    └─ StatsTabRow
       └─ [可滚动区] Column (weight(1f), verticalScroll)
            ├─ AnimatedContent { Tab 内容 }
            └─ StatsAdviceCard
```

### 注意事项

- 固定头部和可滚动区的间距保持一致（16dp）
- `Scaffold` 的 `paddingValues`（含 bottom nav + status bar）仅应用到最外层 Column
- 固定头部内部不再包含 `verticalScroll` 修饰符

## 视觉规范

沿用现有主色板：

```
主色  #1f6f50  ── 折线 / 柱状图达标
强调  #b36a2c  ── PR 标记 / 参考线
浅色  #cc9a62  ── 低于均线
表面  #fffbf4  ── 卡片背景
```

## 实现步骤

1. **StatsDao 新增查询** — `observeWeeklyCardioMinutes`、`observeWeeklyTrainingDays`
2. **FitnessRepository 接口扩展** — 新增 `observeSparklineData()`
3. **OfflineFitnessRepository 实现** — 聚合周数据，返回 SparklineData
4. **StatsUiState 扩展** — 新增 `sparklineData`、`isOverviewReady`、`isTabDataReady`
5. **FitnessViewModel 扩展** — 新增流合并，状态管理
6. **OverviewCard 添加 sparkline** — Canvas 绘制迷你趋势线
7. **AnimatedContent / Crossfade** — Tab 和时间范围切换动效
8. **Cardio 趋势面积渐变** — Vico areaFill 或 Canvas
9. **PR 标记点** — 叠加 Canvas 绘制
10. **Tooltip** — Vico Marker 集成
11. **骨架屏组件** — ShimmerPlaceholder + 各组件空状态改进
12. **粘性头部** — 拆分滚动布局

## 测试

- 无数据时各组件显示正确的占位状态
- 时间范围切换时数据联动正常
- 概览行 sparkline 数据与 Tab 内图表一致
- 粘性头部在内容滚动时保持在可视区域

## 未包含的范围（YAGNI）

- 自定义图表库替换（沿用 Vico + Canvas）
- 页面级入场动画
- 手势缩放图表
- Sparkline 点击交互
