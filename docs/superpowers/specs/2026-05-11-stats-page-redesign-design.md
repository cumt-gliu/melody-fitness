# 统计页重新设计

## 概述

重新设计统计页（StatsScreen），解决当前页面指标孤立、对比不明确的问题。采用**三层结构**布局：固定概览行 → 时间筛选器 + Tab 切换 → 详细 Tab 内容区，让用户能按需查看不同维度的数据对比和趋势。

## 页面结构

```
┌────────────────────────────────────────────┐
│ 📊 训练统计                                │
│ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐       │
│ │本周  │ │有氧  │ │体重  │ │训练  │ ← 概览行
│ │训练 4│ │85 分 │ │-0.8kg│ │天数 3│   (固定)
│ └──────┘ └──────┘ └──────┘ └──────┘       │
│                                             │
│ 时间范围： 本周 │ 本月 │ 本季度 │ 今年      │ ← 筛选器
│                                             │
│ 训练分析 │ 身体指标 │ 力量进步              │ ← Tab 导航
├────────────────────────────────────────────┤
│ Tab 内容区（按选中 Tab + 时间范围刷新）      │
│                                             │
│ ...                                         │
│                                             │
│ 💡 建议卡片（根据当前 Tab/时间变化）        │
└────────────────────────────────────────────┘
```

## 精化需求

### 概览行（固定，不受时间筛选影响）

4 个指标卡片横向排列，每个包含：
- **主数字**：本周数据
- **环比箭头**：vs 上周同期差值，带上周对比值
- **迷你 sparkline**：底部 8 周趋势线

| 指标 | 数据源 | 环比逻辑 |
|------|--------|---------|
| 本周训练 | `workout_logs.dateEpochDay` 本周计数 | 本周 vs 上周同期差值 |
| 有氧时长 | `cardio_entries.durationMinutes` 本周合计 | 本周 vs 上周同期差值 |
| 体重变化 | 最新体重 - 上周同期最新 | 负值绿色（减重），正值红色（增重） |
| 训练天数 | 本周有训练记录的天数 | 本周 vs 上周同期差值 |

### 时间筛选器

位于概览行下方，控制 Tab 内容区的数据范围：

- **本周**：周一至今天
- **本月**：月初至今
- **本季度**：本季度第一天至今
- **今年**：1 月 1 日至今
- **自定义**：起止日期选择（预留，一期可不实现）

Tab 内容区所有图表和指标卡片按选中的时间范围联动刷新。

### Tab 1：训练分析

**图表：**
1. **训练频次柱状图**（已有，增强颜色规则）
   - X 轴：近 8 周
   - Y 轴：训练次数
   - 颜色：达到周均线 = `#1f6f50`，低于周均线 = `#cc9a62`
   - 周均参考线：`#b36a2c` 虚线，标注"周均 N 次"
   - 交互：点击柱子跳转到对应周训练详情

2. **训练类型分布环形图**（新增）
   - 区分力量 vs 各类型有氧
   - 扇区颜色：按 `#1f6f50` / `#4e8a6c` / `#cc9a62` 分配
   - 中心显示总数
   - 交互：触摸扇区高亮并显示占比

3. **有氧时长趋势折线图**（新增）
   - X 轴：按日聚合
   - Y 轴：分钟数
   - 面积渐变填充
   - 底部标注"本期累计 X 分钟 · 日均 X 分钟"

### Tab 2：身体指标

**顶部周期对比行：** 5 个指标卡片横向排列
- 体重 / 体脂 / 腰围 / 睡眠 / 疲劳
- 每个卡片显示 **当前值** + **vs 上期变化箭头**
- 箭头颜色：改善 = 绿色，恶化 = 红色，持平 = 灰色

**图表：**
1. **体重 + 体脂叠加趋势图**（增强已有图表）
   - 主坐标轴：体重折线（`#1f6f50`）
   - 次坐标轴：体脂虚线（`#b36a2c`，虚线 `4,3`）
   - 目标线：当存在体重目标时显示虚线目标线
   - 图例：底部标注

2. **小趋势图行**（新增，3 张迷你折线图）
   - 腰围趋势
   - 睡眠趋势（含 7h 参考线）
   - 疲劳趋势

**交互：** 所有折线图支持触摸数据点显示 tooltip（日期 + 数值）

### Tab 3：力量进步

**顶部动作切换行：** FilterChip 列表
- 自动从历史记录提取所有动作名称
- 选中动作后，下方所有数据联动

**指标行：** 4 个卡片
- 最佳重量（含日期）
- 本次重量（含 vs 上期变化）
- 总容量（weightKg × reps 合计，含变化）
- 组数（含变化）

**图表：**
1. **重量变化趋势折线图**（新增）
   - X 轴：训练日期
   - Y 轴：重量（该次训练该动作的最大重量）
   - PR 标记点：橙色高亮 + "PR N kg" 标签

2. **训练容量趋势折线图**（新增）
   - X 轴：训练日期
   - Y 轴：总容量（weightKg × reps 合计）
   - 底部标注"本期总容量 X kg · 单次平均 X kg"

### 建议卡片

保留在统计页最底部，数据源从"本周"改为"当前筛选周期"，根据当前 Tab 显示对应维度的建议：

| Tab | 建议逻辑 |
|-----|---------|
| 训练分析 | 训练频率、有氧比例 |
| 身体指标 | 睡眠、疲劳、体脂变化 |
| 力量进步 | 容量变化、是否触及 PR |

当无特定方向建议时，显示通用鼓励文案。

### 交互总结

| 元素 | 交互 |
|------|------|
| 时间筛选器 | 点击切换 Tab 内容联动刷新 |
| 概览行卡片 | 点击跳转到对应 Tab |
| 柱状图柱子 | 点击展开该周训练详情列表 |
| 折线图数据点 | 触摸显示 tooltip（日期 + 数值） |
| 环形图扇区 | 触摸高亮 + 显示占比 |
| 动作切换 Chips | 点击切换力量趋势图联动更新 |

## 视觉规范

### 主色板（沿用现有）

```
主色  #1f6f50  ── 折线 / 柱状图达标
辅助  #4e8a6c  ── 辅助数据 / 次要折线
强调  #b36a2c  ── 目标线 / 周均参考线 / PR 标记
浅色  #cc9a62  ── 低于均线警告
背景  #f7f2e8  ── 图表面板
表面  #fffbf4  ── 卡片背景
```

### 新增语义色

```
达标绿  #27ae60  ── 正向变化箭头
警示红  #c0392b  ── 负向变化箭头
警告橙  #f39c12  ── 中等提示
中性灰  #7f8c8d  ── 持平 / 无变化
```

### 颜色规则

- **环比箭头**：改善 = 达标绿，恶化 = 警示红，持平 = 中性灰
- **柱状图**：≥ 周均 = 主色，< 周均 = 浅色
- **趋势线**：主线 = 主色，辅助线 = 强调色（虚线）

## 数据层

### 新增 DAO 查询

```kotlin
// 1. 训练类型分布
@Query("""
    SELECT w.dateEpochDay, c.activityType, c.durationMinutes, c.distanceKm 
    FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id 
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay
""")
fun observeCardioByDateRange(startDay: Long, endDay: Long): Flow<List<CardioByDate>>

// 2. 有氧时长趋势（按日聚合）
@Query("""
    SELECT w.dateEpochDay, SUM(c.durationMinutes) as totalMinutes 
    FROM cardio_entries c JOIN workout_logs w ON c.workoutLogId = w.id 
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay 
    GROUP BY w.dateEpochDay ORDER BY w.dateEpochDay
""")
fun observeCardioDurationTrend(startDay: Long, endDay: Long): Flow<List<CardioDurationPoint>>

// 3. 力量动作历史趋势
@Query("""
    SELECT w.dateEpochDay, e.name, MAX(s.weightKg) as maxWeight, 
           SUM(s.weightKg * s.reps) as volume 
    FROM strength_sets s JOIN strength_exercises e ON s.exerciseId = e.id 
    JOIN workout_logs w ON e.workoutLogId = w.id 
    WHERE w.dateEpochDay BETWEEN :startDay AND :endDay 
    GROUP BY w.dateEpochDay, e.name ORDER BY w.dateEpochDay
""")
fun observeStrengthTrend(startDay: Long, endDay: Long): Flow<List<StrengthTrendRow>>
```

### 新增数据模型

```kotlin
data class WeekOverWeekChanges(
    val workoutCountChange: Int? = null,
    val cardioMinutesChange: Int? = null,
    val weightChange: Float? = null,
    val trainingDaysChange: Int? = null,
)

data class WorkoutTypeCount(
    val type: String,    // "力量", "跑步", "骑行", etc.
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

data class StrengthTrendRow(
    val dateEpochDay: Long,
    val exerciseName: String,
    val maxWeight: Float,
    val volume: Float,
)
```

### StatsUiState 新增字段

```kotlin
data class StatsUiState(
    // ... 现有字段
    // 新增
    val weekOverWeekChanges: WeekOverWeekChanges = WeekOverWeekChanges(),
    val workoutTypeDistribution: List<WorkoutTypeCount> = emptyList(),
    val cardioDurationTrend: List<CardioDurationPoint> = emptyList(),
    val strengthExerciseTrends: Map<String, List<StrengthTrendPoint>> = emptyMap(),
    val selectedExercise: String = "",
    val selectedTimeRange: TimeRange = TimeRange.THIS_WEEK,
)
```

### 时间筛选器

```kotlin
enum class TimeRange(val label: String) {
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    THIS_QUARTER("本季度"),
    THIS_YEAR("今年"),
    CUSTOM("自定义"),
}

// 计算起止日期的辅助方法
fun TimeRange.toDateRange(): Pair<Long, Long> {
    val now = LocalDate.now()
    val start = when (this) {
        TimeRange.THIS_WEEK -> now.with(java.time.DayOfWeek.MONDAY)
        TimeRange.THIS_MONTH -> now.withDayOfMonth(1)
        TimeRange.THIS_QUARTER -> now.with(now.month.firstMonthOfQuarter()).withDayOfMonth(1)
        TimeRange.THIS_YEAR -> now.withDayOfYear(1)
        TimeRange.CUSTOM -> now // 待自定义实现
    }
    return start.toEpochDay() to now.toEpochDay()
}
```

## 实现步骤

1. **新增 DAO 查询** — `StatsDao` 新增 3 个查询方法
2. **新增数据模型** — 添加 WeekOverWeekChanges、WorkoutTypeCount、CardioDurationPoint、StrengthTrendPoint、StrengthTrendRow
3. **扩展 OfflineFitnessRepository** — 新增对应查询方法，对接 DAO
4. **扩展 FitnessRepository 接口** — 新增方法声明
5. **增强 StatsUiState** — 新增字段 + TimeRange 枚举
6. **增强 FitnessViewModel** — 整合新数据 Flow，添加时间筛选状态管理
7. **重构 StatsScreen** — 三层布局 + 概览行 + 时间筛选器 + Tab 导航
8. **实现 Tab 1 训练分析** — 频次柱状图增强 + 环形图 + 有氧趋势图
9. **实现 Tab 2 身体指标** — 周期对比行 + 叠加趋势图 + 迷你趋势图
10. **实现 Tab 3 力量进步** — 动作切换 + 指标行 + 重量趋势 + 容量趋势
11. **更新建议卡片** — 根据 Tab 和时间范围显示建议
12. **测试** — 更新现有测试，覆盖新增逻辑

## 技术选型

- **图表库**：Vico（已集成，`com.nicoulaj:vico-compose-m3`）
- **环形图**：使用 Vico `PieChart` / `DonutChart` 或 Compose Canvas 自定义绘制
- **迷你 sparkline**：Compose Canvas 手绘，无需图表库
- **状态管理**：ViewModel + StateFlow（现有模式）

## 未包含的范围（YAGNI）

- 自定义时间范围选择器（一期仅使用预设筛选，`CUSTOM` 标注为预留）
- 数据导出/截图分享
- 统计页编辑/排序/自定义卡片
- 横向滚动周历选择
