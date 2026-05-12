# 统计页图表设计

## 概述

在统计页（StatsScreen）中嵌入图表，将现有数字卡片转化为可视化趋势图。第一期集成两张图表：**体重趋势折线图** 和 **周训练次数柱状图**。

## 布局

原有数据卡片保留，图表以卡片形式嵌入，整体滚动布局：

```
训练统计
┌──────────────┐ ┌──────────────┐
│ 本周训练 4    │ │ 最新体重      │
└──────────────┘ └──────────────┘
┌────────────────────────────────────┐
│ 📈 体重趋势                       │
│  73 ┤╲                            │
│  72 ┤ ╲  ╱╲  ╱╲                  │
│  71 ┤   ╲╱  ╲╱  ╲╱               │
│     4/1   4/8   4/15  4/22        │
│  ─── 目标 72.0 kg                 │
└────────────────────────────────────┘
┌──────────────┐ ┌──────────────┐
│ 有氧时长      │ │ 有氧距离      │
└──────────────┘ └──────────────┘
┌────────────────────────────────────┐
│ 📊 每周训练次数                   │
│  6 ┤ ██                           │
│  4 ┤ ██ ██ ██                     │
│  2 ┤ ██ ██ ██ ██                  │
│   ──+──+──+──+──                  │
│  ─ ─ 周均 4 次                    │
└────────────────────────────────────┘
┌──────────────┐ ┌──────────────┐
│ 力量最佳      │ │ 体重变化      │
└──────────────┘ └──────────────┘
下一步建议
```

## 图表 1：体重趋势折线图

- **类型**：平滑折线图 + 面积渐变填充
- **数据**：`body_metrics` 按 `dateEpochDay` 升序
- **X 轴**：日期（近 15-30 条记录）
- **Y 轴**：体重（kg/lb 跟随设置）
- **目标线**：当存在体重目标时，显示虚线目标线及标签
- **视觉**：主色 `#1f6f50` 折线，面积从 35% 到 2% 渐变透明

## 图表 2：周训练次数柱状图

- **类型**：柱状图 + 周均参考线
- **数据**：`workout_logs.dateEpochDay` 按周聚合
- **X 轴**：周（近 8 周）
- **Y 轴**：训练次数
- **周均线**：橙色 `#b36a2c` 虚线，标注"周均 N 次"
- **颜色规则**：达到周均线 = `#1f6f50`，低于周均线 = `#cc9a62`

## 交互

| 交互 | 行为 |
|------|------|
| 点击/按住折线图数据点 | 显示 tooltip（日期 + 体重 + 体脂） |
| 点击柱状图柱子 | 进入该周训练详情列表 |

## 颜色集成

图表复用主题色板：

```
主色  #1f6f50  ── 折线 / 柱状图达到均线
中色  #4e8a6c  ── 辅助数据
强调  #b36a2c  ── 目标线 / 周均参考线
浅色  #cc9a62  ── 低于周均线警告
背景  #f7f2e8  ── 图表面板
表面  #fffbf4  ── 卡片背景
```

## 数据依赖

### 现有数据（无需 schema 变更）
- `body_metrics.weightKg` + `body_metrics.dateEpochDay` → 体重趋势
- `workout_logs.dateEpochDay` → 周训练次数

### 需要新增的 DAO 查询

```kotlin
// 体重趋势
@Query("SELECT dateEpochDay, weightKg, bodyFatPercentage FROM body_metrics ORDER BY dateEpochDay ASC")
fun observeBodyMetricTrend(): Flow<List<BodyMetricTrend>>

// 周训练日期
@Query("SELECT dateEpochDay FROM workout_logs ORDER BY dateEpochDay")
fun observeWorkoutDates(): Flow<List<Long>>
```

## 实现步骤

1. 在 `libs.versions.toml` 中添加 Vico 依赖
2. 在 `app/build.gradle.kts` 中引入 `com.nicoulaj:vico-compose-m3`
3. `StatsDao` 新增 `observeBodyMetricTrend()` 和 `observeWorkoutDates()`
4. `OfflineFitnessRepository` 实现对应方法
5. `FitnessRepository` 接口新增方法声明
6. `StatsSnapshot` / `StatsUiState` 扩展图表数据字段
7. `StatsScreen` 中嵌入图表卡片

## 技术选型

**Vico** (`com.nicoulaj:vico-compose-m3:2.1.0`)：
- Compose 原生，无需 View interop
- 支持 Material 3 主题集成
- 折线图、柱状图、组合图
- 内置动画和触摸交互
