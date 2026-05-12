package com.giannisliu.melodyfitness.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giannisliu.melodyfitness.data.repository.GoalInput
import com.giannisliu.melodyfitness.data.repository.GoalType
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseHistory
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class Screen(
    val label: String,
    val badge: String,
) {
    HOME("首页", "今"),
    RECORD("记录", "记"),
    HISTORY("历史", "史"),
    GOALS("目标", "标"),
    STATS("统计", "统"),
    SETTINGS("设置", "设"),
}

@Composable
fun MelodyFitnessApp(
    viewModel: FitnessViewModel,
) {
    val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val recordUiState by viewModel.recordUiState.collectAsStateWithLifecycle()
    val historyUiState by viewModel.historyUiState.collectAsStateWithLifecycle()
    val goalsUiState by viewModel.goalsUiState.collectAsStateWithLifecycle()
    val statsUiState by viewModel.statsUiState.collectAsStateWithLifecycle()
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentScreen == screen) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = screen.badge,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        when (currentScreen) {
            Screen.HOME -> HomeScreen(
                paddingValues = paddingValues,
                uiState = homeUiState,
                weightUnit = settingsUiState.weightUnit,
                onStartRecording = { currentScreen = Screen.RECORD },
                onOpenHistory = { currentScreen = Screen.HISTORY },
            )

            Screen.RECORD -> RecordScreen(
                paddingValues = paddingValues,
                uiState = recordUiState,
                onSaveStrength = viewModel::saveStrengthWorkout,
                onSaveCardio = viewModel::saveCardioWorkout,
                onSaveBodyMetric = viewModel::saveBodyMetric,
            )

            Screen.HISTORY -> HistoryScreen(
                paddingValues = paddingValues,
                uiState = historyUiState,
                onUpdateWorkout = viewModel::updateWorkoutHistoryItem,
                onDeleteWorkout = viewModel::deleteWorkoutHistoryItem,
            )

            Screen.GOALS -> GoalsScreen(
                paddingValues = paddingValues,
                uiState = goalsUiState,
                onSaveGoal = viewModel::saveGoal,
            )

            Screen.STATS -> StatsScreen(
                paddingValues = paddingValues,
                uiState = statsUiState,
                weightUnit = settingsUiState.weightUnit,
                onUpdateTimeRange = viewModel::updateTimeRange,
                onUpdateActiveTab = viewModel::updateActiveTab,
                onSelectExercise = viewModel::selectExercise,
            )

            Screen.SETTINGS -> SettingsScreen(
                paddingValues = paddingValues,
                uiState = settingsUiState,
                onUpdateWeightUnit = viewModel::updateWeightUnit,
                onExportBackup = { viewModel.exportBackupJson() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    paddingValues: PaddingValues,
    uiState: HomeUiState,
    weightUnit: WeightUnit,
    onStartRecording: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF275844),
                            Color(0xFF4E8A6C),
                            Color(0xFFCC9A62),
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "今天也继续积累",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFFFF7EE),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "本周训练 ${uiState.weeklyWorkoutCount} 次${uiState.latestWeightKg?.let { " · 最新体重 ${formatWeight(it, weightUnit)}" } ?: ""}",
                    color = Color(0xFFF7EFE1),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onStartRecording) {
                        Text("开始记录")
                    }
                    TextButton(onClick = onOpenHistory) {
                        Text("查看历史")
                    }
                }
            }
        }

        uiState.latestBodyMetric?.let { metric ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "最新身体状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${metric.dateText} · ${formatWeight(metric.weightKg, weightUnit)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        metric.bodyFatPercentage?.let {
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text("体脂 ${formatFloat(it)}%") },
                            )
                        }
                        metric.waistCm?.let {
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text("腰围 ${formatFloat(it)} cm") },
                            )
                        }
                        metric.sleepHours?.let {
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text("睡眠 ${formatFloat(it)} h") },
                            )
                        }
                        metric.fatigueScore?.let {
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text("疲劳 ${it}/10") },
                            )
                        }
                    }
                    if (metric.notes.isNotBlank()) {
                        Text(
                            text = metric.notes,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionTitle(title = "最近训练")
        if (uiState.recentWorkouts.isEmpty()) {
            EmptyCard(
                title = "还没有训练记录",
                body = "先去记录一次力量、有氧或身体状态，首页就会开始有内容。",
            )
        } else {
            uiState.recentWorkouts.forEach { workout ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = workout.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${workout.dateText} · ${workout.detailText}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    paddingValues: PaddingValues,
    uiState: HistoryUiState,
    onUpdateWorkout: (Long, String, String) -> Unit,
    onDeleteWorkout: (Long) -> Unit,
) {
    var expandedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTitle by rememberSaveable { mutableStateOf("") }
    var editingNotes by rememberSaveable { mutableStateOf("") }

    val editingWorkout = uiState.workouts.firstOrNull { it.id == editingWorkoutId }
    val deletingWorkout = uiState.workouts.firstOrNull { it.id == deletingWorkoutId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(title = "历史记录")
        if (uiState.workouts.isEmpty()) {
            EmptyCard(
                title = "还没有历史训练",
                body = "保存几次训练后，这里会按时间展示每次记录的动作、组数和有氧详情。",
            )
        } else {
            uiState.workouts.forEach { workout ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = workout.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${workout.dateText} · ${workout.detailText}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    expandedWorkoutId =
                                        if (expandedWorkoutId == workout.id) null else workout.id
                                },
                            ) {
                                Text(if (expandedWorkoutId == workout.id) "收起" else "详情")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                onClick = {
                                    editingWorkoutId = workout.id
                                    editingTitle = workout.title
                                    editingNotes = workout.notes
                                },
                            ) {
                                Text("编辑")
                            }
                            TextButton(
                                onClick = { deletingWorkoutId = workout.id },
                            ) {
                                Text("删除")
                            }
                        }

                        if (expandedWorkoutId == workout.id) {
                            HistoryDetailSection(
                                strengthExercises = workout.strengthExercises,
                                cardioSummary = workout.cardioEntries.map {
                                    "${it.activityType} · ${it.durationMinutes} 分钟 · ${formatFloat(it.distanceKm)} km${if (it.averagePace.isNotBlank()) " · ${it.averagePace}" else ""}"
                                },
                                notes = workout.notes,
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingWorkout != null) {
        AlertDialog(
            onDismissRequest = { editingWorkoutId = null },
            title = { Text("编辑历史记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editingTitle,
                        onValueChange = { editingTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("训练标题") },
                    )
                    OutlinedTextField(
                        value = editingNotes,
                        onValueChange = { editingNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("训练备注") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateWorkout(
                            editingWorkout.id,
                            editingTitle,
                            editingNotes,
                        )
                        editingWorkoutId = null
                    },
                    enabled = editingTitle.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWorkoutId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (deletingWorkout != null) {
        AlertDialog(
            onDismissRequest = { deletingWorkoutId = null },
            title = { Text("删除历史记录") },
            text = { Text("确定删除「${deletingWorkout.title}」吗？这会同时删除它下面的动作、组数和有氧记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWorkout(deletingWorkout.id)
                        if (expandedWorkoutId == deletingWorkout.id) {
                            expandedWorkoutId = null
                        }
                        deletingWorkoutId = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWorkoutId = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun HistoryDetailSection(
    strengthExercises: List<StrengthExerciseHistory>,
    cardioSummary: List<String>,
    notes: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (notes.isNotBlank()) {
            Text(
                text = "备注：$notes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (strengthExercises.isNotEmpty()) {
            Text(
                text = "力量动作",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            strengthExercises.forEach { exercise ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("${exercise.name} · ${exercise.setCount} 组", fontWeight = FontWeight.Medium)
                        exercise.sets.forEach { set ->
                            Text(
                                text = "第 ${set.setOrder} 组: ${formatFloat(set.weightKg)} kg x ${set.reps}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (cardioSummary.isNotEmpty()) {
            Text(
                text = "有氧记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            cardioSummary.forEach { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsScreen(
    paddingValues: PaddingValues,
    uiState: GoalsUiState,
    onSaveGoal: (GoalInput) -> Unit,
) {
    var goalType by rememberSaveable { mutableStateOf(GoalType.WEIGHT) }
    var title by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("kg") }
    var startText by rememberSaveable { mutableStateOf("") }
    var currentText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(title = "目标设置")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GoalType.entries.forEach { type ->
                FilterChip(
                    selected = goalType == type,
                    onClick = {
                        goalType = type
                        unit = when (type) {
                            GoalType.WEIGHT -> "kg"
                            GoalType.FREQUENCY -> "次"
                            GoalType.PERFORMANCE -> "kg"
                        }
                    },
                    label = { Text(type.label) },
                )
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("目标名称") },
            placeholder = { Text("例如：体重降到 72kg") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                modifier = Modifier.width(100.dp),
                label = { Text("单位") },
            )
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                modifier = Modifier.weight(1f),
                label = { Text("起点") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = currentText,
                onValueChange = { currentText = it },
                modifier = Modifier.weight(1f),
                label = { Text("当前值") },
            )
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                modifier = Modifier.weight(1f),
                label = { Text("目标值") },
            )
        }
        Button(
            onClick = {
                onSaveGoal(
                    GoalInput(
                        title = title,
                        type = goalType,
                        startValue = startText.toFloat(),
                        currentValue = currentText.toFloat(),
                        targetValue = targetText.toFloat(),
                        unit = unit,
                    ),
                )
                title = ""
                startText = ""
                currentText = ""
                targetText = ""
            },
            enabled = title.isNotBlank() &&
                startText.toFloatOrNull() != null &&
                currentText.toFloatOrNull() != null &&
                targetText.toFloatOrNull() != null &&
                unit.isNotBlank(),
        ) {
            Text("保存目标")
        }

        SectionTitle(title = "当前目标")
        if (uiState.goals.isEmpty()) {
            EmptyCard(
                title = "还没有目标",
                body = "先添加一个体重、频率或表现目标，后面就能看到进度条。",
            )
        } else {
            uiState.goals.forEach { goal ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(goal.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    goal.typeLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("${goal.progressPercent}%")
                        }
                        LinearProgressIndicator(
                            progress = { goal.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "当前 ${formatFloat(goal.currentValue)} ${goal.unit} / 目标 ${formatFloat(goal.targetValue)} ${goal.unit}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    paddingValues: PaddingValues,
    uiState: SettingsUiState,
    onUpdateWeightUnit: (WeightUnit) -> Unit,
    onExportBackup: suspend () -> String,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(title = "设置")
            Text(
                text = "体重显示单位",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = uiState.weightUnit == unit,
                        onClick = { onUpdateWeightUnit(unit) },
                        label = { Text("${unit.label} (${unit.symbol})") },
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "导出备份",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "把当前训练历史、目标、统计和设置导出为 JSON 文件，保存到应用文件目录。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val json = onExportBackup()
                                val exportDir = context.getExternalFilesDir(null) ?: context.filesDir
                                val fileName = "melody-backup-" + LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json"
                                val outputFile = File(exportDir, fileName)
                                outputFile.writeText(json)
                                snackbarHostState.showSnackbar("备份已导出到 ${outputFile.absolutePath}")
                            }
                        },
                    ) {
                        Text("导出 JSON 备份")
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
internal fun EmptyCard(
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun formatFloat(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}

internal fun formatWeight(valueKg: Float, weightUnit: WeightUnit): String {
    return when (weightUnit) {
        WeightUnit.KG -> "${formatFloat(valueKg)} kg"
        WeightUnit.LB -> "${formatFloat(valueKg * 2.20462f)} lb"
    }
}
