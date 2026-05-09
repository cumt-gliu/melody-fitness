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
import androidx.compose.runtime.mutableStateListOf
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
import com.giannisliu.melodyfitness.data.repository.BodyMetricInput
import com.giannisliu.melodyfitness.data.repository.CardioWorkoutInput
import com.giannisliu.melodyfitness.data.repository.GoalInput
import com.giannisliu.melodyfitness.data.repository.GoalType
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseHistory
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseInput
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseTemplate
import com.giannisliu.melodyfitness.data.repository.StrengthSetInput
import com.giannisliu.melodyfitness.data.repository.StrengthWorkoutInput
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import com.giannisliu.melodyfitness.domain.WorkoutCatalog
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

private data class StrengthSetDraft(
    val weightText: String = "",
    val repsText: String = "",
)

private data class StrengthExerciseDraft(
    val name: String = "",
    val sets: List<StrengthSetDraft> = listOf(StrengthSetDraft()),
)

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordScreen(
    paddingValues: PaddingValues,
    uiState: RecordUiState,
    onSaveStrength: (StrengthWorkoutInput) -> Unit,
    onSaveCardio: (CardioWorkoutInput) -> Unit,
    onSaveBodyMetric: (BodyMetricInput) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("strength") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var workoutTitle by rememberSaveable { mutableStateOf("") }
    var workoutNotes by rememberSaveable { mutableStateOf("") }
    val strengthExercises = remember { mutableStateListOf(StrengthExerciseDraft()) }

    var cardioType by rememberSaveable { mutableStateOf("") }
    var durationText by rememberSaveable { mutableStateOf("") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var paceText by rememberSaveable { mutableStateOf("") }
    var cardioNotes by rememberSaveable { mutableStateOf("") }

    var bodyWeightText by rememberSaveable { mutableStateOf("") }
    var bodyFatText by rememberSaveable { mutableStateOf("") }
    var waistText by rememberSaveable { mutableStateOf("") }
    var sleepHoursText by rememberSaveable { mutableStateOf("") }
    var fatigueScoreText by rememberSaveable { mutableStateOf("") }
    var bodyNotes by rememberSaveable { mutableStateOf("") }

    fun updateExercise(index: Int, transform: (StrengthExerciseDraft) -> StrengthExerciseDraft) {
        strengthExercises[index] = transform(strengthExercises[index])
    }

    fun addExercise() {
        strengthExercises.add(StrengthExerciseDraft())
    }

    fun removeExercise(index: Int) {
        if (strengthExercises.size > 1) {
            strengthExercises.removeAt(index)
        }
    }

    fun addSet(exerciseIndex: Int) {
        updateExercise(exerciseIndex) { exercise ->
            val previousSet = exercise.sets.lastOrNull() ?: StrengthSetDraft()
            exercise.copy(
                sets = exercise.sets + StrengthSetDraft(
                    weightText = previousSet.weightText,
                    repsText = previousSet.repsText,
                ),
            )
        }
    }

    fun applyTemplate(template: StrengthExerciseTemplate) {
        val draftedExercise = StrengthExerciseDraft(
            name = template.name,
            sets = listOf(
                StrengthSetDraft(
                    weightText = formatFloat(template.lastWeightKg),
                    repsText = template.lastReps.toString(),
                ),
            ),
        )
        if (strengthExercises.size == 1 &&
            strengthExercises.first().name.isBlank() &&
            strengthExercises.first().sets.size == 1 &&
            strengthExercises.first().sets.first().weightText.isBlank() &&
            strengthExercises.first().sets.first().repsText.isBlank()
        ) {
            strengthExercises[0] = draftedExercise
        } else {
            strengthExercises.add(draftedExercise)
        }
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        updateExercise(exerciseIndex) { exercise ->
            if (exercise.sets.size == 1) {
                exercise
            } else {
                exercise.copy(
                    sets = exercise.sets.filterIndexed { index, _ -> index != setIndex },
                )
            }
        }
    }

    val isStrengthValid = strengthExercises.isNotEmpty() &&
        strengthExercises.all { exercise ->
            exercise.name.isNotBlank() &&
                exercise.sets.isNotEmpty() &&
                exercise.sets.all { set ->
                    set.weightText.toFloatOrNull() != null && set.repsText.toIntOrNull() != null
                }
        }

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
            SectionTitle(title = "记录中心")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkoutCatalog.recordModes.forEach { recordMode ->
                    FilterChip(
                        selected = mode == recordMode.id,
                        onClick = { mode = recordMode.id },
                        label = { Text(recordMode.label) },
                    )
                }
            }

            when (mode) {
                "strength" -> {
                    if (uiState.strengthTemplates.isNotEmpty()) {
                        Text(
                            text = "最近常用动作",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.strengthTemplates.take(6).forEach { template ->
                                FilterChip(
                                    selected = false,
                                    onClick = { applyTemplate(template) },
                                    label = {
                                        Text(
                                            "${template.name} · ${formatFloat(template.lastWeightKg)}kg x ${template.lastReps}",
                                        )
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = workoutTitle,
                        onValueChange = { workoutTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("训练标题") },
                        placeholder = { Text("例如：胸肩训练") },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WorkoutCatalog.strengthTitlePresets.forEach { preset ->
                            FilterChip(
                                selected = workoutTitle == preset,
                                onClick = { workoutTitle = preset },
                                label = { Text(preset) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = workoutNotes,
                        onValueChange = { workoutNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("训练备注") },
                        placeholder = { Text("例如：整体状态不错，后段疲劳上升") },
                    )

                    strengthExercises.forEachIndexed { exerciseIndex, exercise ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "动作 ${exerciseIndex + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (strengthExercises.size > 1) {
                                        TextButton(onClick = { removeExercise(exerciseIndex) }) {
                                            Text("删除动作")
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = exercise.name,
                                    onValueChange = { name ->
                                        updateExercise(exerciseIndex) { it.copy(name = name) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("动作名称") },
                                    placeholder = { Text("例如：卧推") },
                                )
                                PresetExerciseChips(
                                    title = "徒手基础",
                                    presets = WorkoutCatalog.bodyweightExercisePresets,
                                    selectedExerciseName = exercise.name,
                                    onPresetSelected = { preset ->
                                        updateExercise(exerciseIndex) { it.copy(name = preset) }
                                    },
                                )
                                PresetExerciseChips(
                                    title = "扣篮专项",
                                    presets = WorkoutCatalog.dunkExercisePresets,
                                    selectedExerciseName = exercise.name,
                                    onPresetSelected = { preset ->
                                        updateExercise(exerciseIndex) { it.copy(name = preset) }
                                    },
                                )
                                exercise.sets.forEachIndexed { setIndex, set ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = "第 ${setIndex + 1} 组",
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                if (exercise.sets.size > 1) {
                                                    TextButton(
                                                        onClick = { removeSet(exerciseIndex, setIndex) },
                                                    ) {
                                                        Text("删除组")
                                                    }
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                OutlinedTextField(
                                                    value = set.weightText,
                                                    onValueChange = { weight ->
                                                        updateExercise(exerciseIndex) { draft ->
                                                            draft.copy(
                                                                sets = draft.sets.mapIndexed { index, item ->
                                                                    if (index == setIndex) {
                                                                        item.copy(weightText = weight)
                                                                    } else {
                                                                        item
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    label = { Text("重量 kg") },
                                                )
                                                OutlinedTextField(
                                                    value = set.repsText,
                                                    onValueChange = { reps ->
                                                        updateExercise(exerciseIndex) { draft ->
                                                            draft.copy(
                                                                sets = draft.sets.mapIndexed { index, item ->
                                                                    if (index == setIndex) {
                                                                        item.copy(repsText = reps)
                                                                    } else {
                                                                        item
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    label = { Text("次数") },
                                                )
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick = { addSet(exerciseIndex) }) {
                                    Text("新增一组")
                                }
                            }
                        }
                    }

                    TextButton(onClick = ::addExercise) {
                        Text("新增一个动作")
                    }
                    Button(
                        onClick = {
                            onSaveStrength(
                                StrengthWorkoutInput(
                                    title = workoutTitle,
                                    notes = workoutNotes,
                                    exercises = strengthExercises.map { exercise ->
                                        StrengthExerciseInput(
                                            name = exercise.name,
                                            sets = exercise.sets.map { set ->
                                                StrengthSetInput(
                                                    weightKg = set.weightText.toFloat(),
                                                    reps = set.repsText.toInt(),
                                                )
                                            },
                                        )
                                    },
                                ),
                            )
                            workoutTitle = ""
                            workoutNotes = ""
                            strengthExercises.clear()
                            strengthExercises.add(StrengthExerciseDraft())
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("多动作训练已保存到本地")
                            }
                        },
                        enabled = isStrengthValid,
                    ) {
                        Text("保存力量训练")
                    }
                }

                "cardio" -> {
                    OutlinedTextField(
                        value = cardioType,
                        onValueChange = { cardioType = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("项目") },
                        placeholder = { Text("例如：跑步") },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WorkoutCatalog.cardioActivityPresets.forEach { preset ->
                            FilterChip(
                                selected = cardioType == preset,
                                onClick = { cardioType = preset },
                                label = { Text(preset) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("时长 分钟") },
                        )
                        OutlinedTextField(
                            value = distanceText,
                            onValueChange = { distanceText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("距离 km") },
                        )
                    }
                    OutlinedTextField(
                        value = paceText,
                        onValueChange = { paceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("配速 / 平均心率") },
                        placeholder = { Text("例如：5'40\" 或 150 bpm") },
                    )
                    OutlinedTextField(
                        value = cardioNotes,
                        onValueChange = { cardioNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注") },
                    )
                    Button(
                        onClick = {
                            onSaveCardio(
                                CardioWorkoutInput(
                                    activityType = cardioType,
                                    durationMinutes = durationText.toInt(),
                                    distanceKm = distanceText.toFloat(),
                                    averagePace = paceText,
                                    notes = cardioNotes,
                                ),
                            )
                            cardioType = ""
                            durationText = ""
                            distanceText = ""
                            paceText = ""
                            cardioNotes = ""
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("有氧训练已保存到本地")
                            }
                        },
                        enabled = cardioType.isNotBlank() &&
                            durationText.toIntOrNull() != null &&
                            distanceText.toFloatOrNull() != null,
                    ) {
                        Text("保存有氧训练")
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = bodyWeightText,
                        onValueChange = { bodyWeightText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("体重 kg") },
                    )
                    OutlinedTextField(
                        value = bodyFatText,
                        onValueChange = { bodyFatText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("体脂 %（可选）") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = waistText,
                            onValueChange = { waistText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("腰围 cm（可选）") },
                        )
                        OutlinedTextField(
                            value = sleepHoursText,
                            onValueChange = { sleepHoursText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("睡眠 小时（可选）") },
                        )
                    }
                    OutlinedTextField(
                        value = fatigueScoreText,
                        onValueChange = { fatigueScoreText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("疲劳感 1-10（可选）") },
                    )
                    OutlinedTextField(
                        value = bodyNotes,
                        onValueChange = { bodyNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注") },
                        placeholder = { Text("例如：睡眠一般，恢复偏慢") },
                    )
                    Button(
                        onClick = {
                            onSaveBodyMetric(
                                BodyMetricInput(
                                    weightKg = bodyWeightText.toFloat(),
                                    bodyFatPercentage = bodyFatText.toFloatOrNull(),
                                    waistCm = waistText.toFloatOrNull(),
                                    sleepHours = sleepHoursText.toFloatOrNull(),
                                    fatigueScore = fatigueScoreText.toIntOrNull(),
                                    notes = bodyNotes,
                                ),
                            )
                            bodyWeightText = ""
                            bodyFatText = ""
                            waistText = ""
                            sleepHoursText = ""
                            fatigueScoreText = ""
                            bodyNotes = ""
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("身体状态已保存到本地")
                            }
                        },
                        enabled = bodyWeightText.toFloatOrNull() != null &&
                            (fatigueScoreText.isBlank() || fatigueScoreText.toIntOrNull() in 1..10),
                    ) {
                        Text("保存身体状态")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetExerciseChips(
    title: String,
    presets: List<String>,
    selectedExerciseName: String,
    onPresetSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = selectedExerciseName == preset,
                    onClick = { onPresetSelected(preset) },
                    label = { Text(preset) },
                )
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

@Composable
private fun StatsScreen(
    paddingValues: PaddingValues,
    uiState: StatsUiState,
    weightUnit: WeightUnit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "本周训练",
                value = "${uiState.weeklyWorkoutCount} 次",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "最新体重",
                value = uiState.latestWeightKg?.let { formatWeight(it, weightUnit) } ?: "--",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "有氧时长",
                value = "${uiState.totalCardioMinutes} 分钟",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "有氧距离",
                value = "${formatFloat(uiState.totalCardioDistanceKm)} km",
            )
        }
        MetricCard(
            modifier = Modifier.fillMaxWidth(),
            title = "力量最佳重量",
            value = "${formatFloat(uiState.bestStrengthWeightKg)} kg",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "较上次体重变化",
                value = uiState.weightChangeSinceLastRecordKg?.let {
                    val prefix = if (it > 0f) "+" else ""
                    "$prefix${formatFloat(it)} kg"
                } ?: "--",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "最近睡眠",
                value = uiState.latestSleepHours?.let { "${formatFloat(it)} 小时" } ?: "--",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "最近腰围",
                value = uiState.latestWaistCm?.let { "${formatFloat(it)} cm" } ?: "--",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "疲劳感",
                value = uiState.latestFatigueScore?.let { "$it / 10" } ?: "--",
            )
        }
        uiState.latestBodyFatPercentage?.let {
            MetricCard(
                modifier = Modifier.fillMaxWidth(),
                title = "最新体脂",
                value = "${formatFloat(it)} %",
            )
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "下一步建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        uiState.latestFatigueScore != null && uiState.latestFatigueScore >= 8 ->
                            "最近疲劳感偏高，下一次训练可以主动降一点容量，先把恢复拉回来。"
                        uiState.latestSleepHours != null && uiState.latestSleepHours < 6f ->
                            "最近睡眠偏少，先优先补睡眠，再观察体重和力量波动。"
                        uiState.weeklyWorkoutCount < 3 -> "这周训练次数还不多，可以优先把频率补上。"
                        uiState.totalCardioMinutes < 60 -> "有氧累计偏少，可以补一到两次 20-30 分钟中低强度训练。"
                        else -> "本周训练节奏不错，继续观察体重和力量的联动变化。"
                    },
                )
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
private fun SectionTitle(title: String) {
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
private fun EmptyCard(
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

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatFloat(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}

private fun formatWeight(valueKg: Float, weightUnit: WeightUnit): String {
    return when (weightUnit) {
        WeightUnit.KG -> "${formatFloat(valueKg)} kg"
        WeightUnit.LB -> "${formatFloat(valueKg * 2.20462f)} lb"
    }
}
