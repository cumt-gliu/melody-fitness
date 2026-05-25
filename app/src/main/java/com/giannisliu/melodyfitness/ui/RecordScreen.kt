package com.giannisliu.melodyfitness.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.giannisliu.melodyfitness.data.repository.BodyMetricInput
import com.giannisliu.melodyfitness.data.repository.CardioWorkoutInput
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseInput
import com.giannisliu.melodyfitness.data.repository.StrengthExerciseTemplate
import com.giannisliu.melodyfitness.data.repository.StrengthWorkoutInput
import com.giannisliu.melodyfitness.domain.WorkoutCatalog
import kotlinx.coroutines.launch

private enum class RecordMode(val label: String) {
    STRENGTH("力量训练"),
    BODYWEIGHT("自重训练"),
    CARDIO("有氧训练"),
    BODY("身体状态"),
}

private data class StrengthSetDraft(
    val weightText: String = "",
    val repsText: String = "",
    val isBodyweight: Boolean = false,
)

private data class StrengthExerciseDraft(
    val name: String = "",
    val sets: List<StrengthSetDraft> = listOf(StrengthSetDraft()),
)

@Composable
fun RecordScreen(
    paddingValues: PaddingValues,
    uiState: RecordUiState,
    onSaveStrength: (StrengthWorkoutInput) -> Unit,
    onSaveCardio: (CardioWorkoutInput) -> Unit,
    onSaveBodyMetric: (BodyMetricInput) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(RecordMode.STRENGTH) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<RecordMode?>(null) }
    var isDirty by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun switchMode(newMode: RecordMode) {
        if (isDirty && newMode != mode) {
            pendingMode = newMode
            showDiscardDialog = true
        } else {
            mode = newMode
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(innerPadding),
        ) {
            // Fixed header
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionTitle(title = "记录中心")

                // Mode tabs — primary style, visually prominent
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordMode.entries.forEach { recordMode ->
                        FilterChip(
                            selected = mode == recordMode,
                            onClick = { switchMode(recordMode) },
                            label = { Text(recordMode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // Mode content — each form handles scroll + sticky button internally
            when (mode) {
                RecordMode.STRENGTH -> StrengthWorkoutForm(
                    templates = uiState.strengthTemplates,
                    onDirtyChanged = { isDirty = it },
                    onSave = { input ->
                        onSaveStrength(input)
                        isDirty = false
                    },
                )

                RecordMode.BODYWEIGHT -> StrengthWorkoutForm(
                    templates = uiState.strengthTemplates,
                    defaultTitle = "徒手训练",
                    bodyweightMode = true,
                    onDirtyChanged = { isDirty = it },
                    onSave = { input ->
                        onSaveStrength(input)
                        isDirty = false
                    },
                )

                RecordMode.CARDIO -> CardioWorkoutForm(
                    onDirtyChanged = { isDirty = it },
                    onSave = { input ->
                        onSaveCardio(input)
                        isDirty = false
                    },
                )

                RecordMode.BODY -> BodyMetricForm(
                    onDirtyChanged = { isDirty = it },
                    onSave = { input ->
                        onSaveBodyMetric(input)
                        isDirty = false
                    },
                )
            }
        }
    }

    // Discard confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                pendingMode = null
            },
            title = { Text("丢弃未保存的内容？") },
            text = { Text("当前填写的记录还没有保存，切换后将丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMode?.let { mode = it }
                        isDirty = false
                        showDiscardDialog = false
                        pendingMode = null
                    },
                ) {
                    Text("丢弃")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        pendingMode = null
                    },
                ) {
                    Text("继续编辑")
                }
            },
        )
    }
}

// ─── Strength Workout Form ──────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrengthWorkoutForm(
    templates: List<StrengthExerciseTemplate>,
    defaultTitle: String = "",
    bodyweightMode: Boolean = false,
    onDirtyChanged: (Boolean) -> Unit,
    onSave: (StrengthWorkoutInput) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var workoutTitle by rememberSaveable { mutableStateOf(defaultTitle) }
    var workoutNotes by rememberSaveable { mutableStateOf("") }
    val strengthExercises = remember {
        mutableStateListOf(
            StrengthExerciseDraft(
                sets = listOf(StrengthSetDraft(isBodyweight = bodyweightMode)),
            ),
        )
    }

    fun markDirty() {
        onDirtyChanged(true)
    }

    fun updateExercise(index: Int, transform: (StrengthExerciseDraft) -> StrengthExerciseDraft) {
        strengthExercises[index] = transform(strengthExercises[index])
        markDirty()
    }

    fun addExercise() {
        strengthExercises.add(
            StrengthExerciseDraft(
                sets = listOf(StrengthSetDraft(isBodyweight = bodyweightMode)),
            ),
        )
        markDirty()
    }

    fun removeExercise(index: Int) {
        if (strengthExercises.size > 1) {
            strengthExercises.removeAt(index)
            markDirty()
        }
    }

    fun addSet(exerciseIndex: Int) {
        updateExercise(exerciseIndex) { exercise ->
            val previousSet = exercise.sets.lastOrNull() ?: StrengthSetDraft()
            exercise.copy(
                sets = exercise.sets + StrengthSetDraft(
                    weightText = previousSet.weightText,
                    repsText = previousSet.repsText,
                    isBodyweight = previousSet.isBodyweight,
                ),
            )
        }
    }

    fun applyTemplate(template: StrengthExerciseTemplate) {
        val isBodyweight = template.lastWeightKg == 0f || isBodyweightExerciseName(template.name)
        val draftedExercise = StrengthExerciseDraft(
            name = template.name,
            sets = listOf(
                StrengthSetDraft(
                    weightText = if (isBodyweight) "" else formatFloat(template.lastWeightKg),
                    repsText = template.lastReps.toString(),
                    isBodyweight = isBodyweight,
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
        markDirty()
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        updateExercise(exerciseIndex) { exercise ->
            if (exercise.sets.size == 1) exercise
            else exercise.copy(sets = exercise.sets.filterIndexed { i, _ -> i != setIndex })
        }
    }

    fun resetForm() {
        workoutTitle = defaultTitle
        workoutNotes = ""
        strengthExercises.clear()
        strengthExercises.add(
            StrengthExerciseDraft(
                sets = listOf(StrengthSetDraft(isBodyweight = bodyweightMode)),
            ),
        )
        onDirtyChanged(false)
    }

    val hasEmptyName = strengthExercises.any { it.name.isBlank() }
    val hasInvalidSet = strengthExercises.any { exercise ->
        exercise.sets.any { set ->
            parseStrengthSetInput(set.toFormState()) == null
        }
    }
    val isValid = strengthExercises.isNotEmpty() && !hasEmptyName && !hasInvalidSet

    val validationMessage = when {
        hasEmptyName -> "请填写所有动作名称"
        hasInvalidSet -> "请填写每组的次数；非自重组还需要重量"
        else -> null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (bodyweightMode) {
                    Text(
                        text = "选择动作后只需要填次数或秒数；有负重时再填负重 kg。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Templates
                val visibleTemplates = if (bodyweightMode) {
                    templates.filter { it.lastWeightKg == 0f || isBodyweightExerciseName(it.name) }
                } else {
                    templates
                }
                if (visibleTemplates.isNotEmpty()) {
                    Text(
                        text = "最近常用动作",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visibleTemplates.take(6).forEach { template ->
                            AssistChip(
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

                // Title
                OutlinedTextField(
                    value = workoutTitle,
                    onValueChange = { workoutTitle = it; markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("训练标题") },
                    placeholder = { Text("例如：胸肩训练") },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val titlePresets = if (bodyweightMode) {
                        listOf("徒手训练", "核心训练", "弹跳训练", "扣篮专项", "爆发力训练")
                    } else {
                        WorkoutCatalog.strengthTitlePresets
                    }
                    titlePresets.forEach { preset ->
                        FilterChip(
                            selected = workoutTitle == preset,
                            onClick = { workoutTitle = preset; markDirty() },
                            label = { Text(preset) },
                        )
                    }
                }

                // Notes
                OutlinedTextField(
                    value = workoutNotes,
                    onValueChange = { workoutNotes = it; markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("训练备注") },
                    placeholder = { Text("例如：整体状态不错，后段疲劳上升") },
                )

                // Exercises
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
                                isError = exercise.name.isBlank() && hasEmptyName,
                            )
                            PresetExerciseChips(
                                title = "徒手基础",
                                presets = WorkoutCatalog.bodyweightExercisePresets,
                                selectedExerciseName = exercise.name,
                                onPresetSelected = { preset ->
                                    updateExercise(exerciseIndex) {
                                        it.copy(
                                            name = preset,
                                            sets = it.sets.map { set ->
                                                set.copy(weightText = "", isBodyweight = true)
                                            },
                                        )
                                    }
                                },
                            )
                            PresetExerciseChips(
                                title = "扣篮专项",
                                presets = WorkoutCatalog.dunkExercisePresets,
                                selectedExerciseName = exercise.name,
                                onPresetSelected = { preset ->
                                    updateExercise(exerciseIndex) {
                                        it.copy(
                                            name = preset,
                                            sets = it.sets.map { set ->
                                                set.copy(weightText = "", isBodyweight = true)
                                            },
                                        )
                                    }
                                },
                            )

                            // Sets
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
                                                            sets = draft.sets.mapIndexed { i, item ->
                                                                if (i == setIndex) item.copy(weightText = weight) else item
                                                            },
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = { Text(if (set.isBodyweight) "负重 kg（可选）" else "重量 kg") },
                                                placeholder = { Text(if (set.isBodyweight) "自重" else "例如：60") },
                                                isError = set.weightText.isNotBlank() &&
                                                    set.weightText.toFloatOrNull() == null,
                                            )
                                            OutlinedTextField(
                                                value = set.repsText,
                                                onValueChange = { reps ->
                                                    updateExercise(exerciseIndex) { draft ->
                                                        draft.copy(
                                                            sets = draft.sets.mapIndexed { i, item ->
                                                                if (i == setIndex) item.copy(repsText = reps) else item
                                                            },
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("次数 / 秒数") },
                                                isError = set.repsText.isNotBlank() &&
                                                    (set.repsText.toIntOrNull()?.takeIf { it > 0 } == null),
                                            )
                                        }
                                        FilterChip(
                                            selected = set.isBodyweight,
                                            onClick = {
                                                updateExercise(exerciseIndex) { draft ->
                                                    draft.copy(
                                                        sets = draft.sets.mapIndexed { i, item ->
                                                            if (i == setIndex) {
                                                                item.copy(
                                                                    weightText = if (item.isBodyweight) item.weightText else "",
                                                                    isBodyweight = !item.isBodyweight,
                                                                )
                                                            } else {
                                                                item
                                                            }
                                                        },
                                                    )
                                                }
                                            },
                                            label = { Text(if (set.isBodyweight) "自重组" else "改为自重") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            ),
                                        )
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

                // Bottom spacer so content doesn't hide behind sticky button
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Sticky save
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (validationMessage != null) {
                        Text(
                            text = validationMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = {
                            onSave(
                                StrengthWorkoutInput(
                                    title = workoutTitle,
                                    notes = workoutNotes,
                                    exercises = strengthExercises.map { exercise ->
                                        StrengthExerciseInput(
                                            name = exercise.name,
                                            sets = exercise.sets.map { set ->
                                                requireNotNull(parseStrengthSetInput(set.toFormState()))
                                            },
                                        )
                                    },
                                ),
                            )
                            resetForm()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("力量训练已保存 ✓")
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存力量训练")
                    }
                }
            }
        }
    }
}

private fun StrengthSetDraft.toFormState(): StrengthSetFormState {
    return StrengthSetFormState(
        weightText = weightText,
        repsText = repsText,
        isBodyweight = isBodyweight,
    )
}

// ─── Cardio Workout Form ────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioWorkoutForm(
    onDirtyChanged: (Boolean) -> Unit,
    onSave: (CardioWorkoutInput) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var cardioType by rememberSaveable { mutableStateOf("") }
    var durationText by rememberSaveable { mutableStateOf("") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var paceText by rememberSaveable { mutableStateOf("") }
    var cardioNotes by rememberSaveable { mutableStateOf("") }

    fun markDirty() {
        onDirtyChanged(true)
    }

    val isValid = cardioType.isNotBlank() &&
        durationText.toFloatOrNull() != null &&
        distanceText.toFloatOrNull() != null

    val validationMessage = when {
        cardioType.isBlank() -> "请填写有氧项目"
        durationText.toFloatOrNull() == null -> "请填写有效的时长（分钟）"
        distanceText.toFloatOrNull() == null -> "请填写有效的距离（km）"
        else -> null
    }

    fun resetForm() {
        cardioType = ""
        durationText = ""
        distanceText = ""
        paceText = ""
        cardioNotes = ""
        onDirtyChanged(false)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = cardioType,
                    onValueChange = { cardioType = it; markDirty() },
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
                            onClick = { cardioType = preset; markDirty() },
                            label = { Text(preset) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it; markDirty() },
                        modifier = Modifier.weight(1f),
                        label = { Text("时长（分钟）") },
                        isError = durationText.isNotBlank() && durationText.toFloatOrNull() == null,
                    )
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it; markDirty() },
                        modifier = Modifier.weight(1f),
                        label = { Text("距离 km") },
                        isError = distanceText.isNotBlank() && distanceText.toFloatOrNull() == null,
                    )
                }
                OutlinedTextField(
                    value = paceText,
                    onValueChange = { paceText = it; markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("配速 / 平均心率") },
                    placeholder = { Text("例如：5'40\" 或 150 bpm") },
                )
                OutlinedTextField(
                    value = cardioNotes,
                    onValueChange = { cardioNotes = it; markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (validationMessage != null) {
                        Text(
                            text = validationMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = {
                            onSave(
                                CardioWorkoutInput(
                                    activityType = cardioType,
                                    durationMinutes = durationText.toInt(),
                                    distanceKm = distanceText.toFloat(),
                                    averagePace = paceText,
                                    notes = cardioNotes,
                                ),
                            )
                            resetForm()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("有氧训练已保存 ✓")
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存有氧训练")
                    }
                }
            }
        }
    }
}

// ─── Body Metric Form ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyMetricForm(
    onDirtyChanged: (Boolean) -> Unit,
    onSave: (BodyMetricInput) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bodyWeightText by rememberSaveable { mutableStateOf("") }
    var bodyFatText by rememberSaveable { mutableStateOf("") }
    var waistText by rememberSaveable { mutableStateOf("") }
    var sleepHoursText by rememberSaveable { mutableStateOf("") }
    var fatigueScoreText by rememberSaveable { mutableStateOf("") }
    var bodyNotes by rememberSaveable { mutableStateOf("") }
    var showOptional by remember { mutableStateOf(false) }

    fun markDirty() {
        onDirtyChanged(true)
    }

    val isValid = bodyWeightText.toFloatOrNull() != null &&
        (fatigueScoreText.isBlank() || fatigueScoreText.toIntOrNull() in 1..10)

    val validationMessage = when {
        bodyWeightText.toFloatOrNull() == null && bodyWeightText.isNotBlank() -> "请输入有效数字"
        bodyWeightText.toFloatOrNull() == null -> null // don't nag on empty weight
        fatigueScoreText.isNotBlank() && fatigueScoreText.toIntOrNull() !in 1..10 -> "疲劳感请在 1-10 之间"
        else -> null
    }

    fun resetForm() {
        bodyWeightText = ""
        bodyFatText = ""
        waistText = ""
        sleepHoursText = ""
        fatigueScoreText = ""
        bodyNotes = ""
        showOptional = false
        onDirtyChanged(false)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Required: weight
                OutlinedTextField(
                    value = bodyWeightText,
                    onValueChange = { bodyWeightText = it; markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("体重 kg *") },
                    isError = bodyWeightText.isNotBlank() && bodyWeightText.toFloatOrNull() == null,
                )

                // Optional fields toggle
                TextButton(onClick = { showOptional = !showOptional }) {
                    Text(
                        if (showOptional) "收起附加指标" else "附加指标（体脂 / 腰围 / 睡眠 / 疲劳）",
                    )
                }

                AnimatedVisibility(
                    visible = showOptional,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = bodyFatText,
                                onValueChange = { bodyFatText = it; markDirty() },
                                modifier = Modifier.weight(1f),
                                label = { Text("体脂 %") },
                            )
                            OutlinedTextField(
                                value = waistText,
                                onValueChange = { waistText = it; markDirty() },
                                modifier = Modifier.weight(1f),
                                label = { Text("腰围 cm") },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = sleepHoursText,
                                onValueChange = { sleepHoursText = it; markDirty() },
                                modifier = Modifier.weight(1f),
                                label = { Text("睡眠（小时）") },
                            )
                            OutlinedTextField(
                                value = fatigueScoreText,
                                onValueChange = { fatigueScoreText = it; markDirty() },
                                modifier = Modifier.weight(1f),
                                label = { Text("疲劳感 1-10") },
                                isError = fatigueScoreText.isNotBlank() && fatigueScoreText.toIntOrNull() !in 1..10,
                            )
                        }
                    }
                }

                // Notes (always visible)
                OutlinedTextField(
                    value = bodyNotes,
                    onValueChange = { bodyNotes = it; markDirty() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    placeholder = { Text("例如：睡眠一般，恢复偏慢") },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (validationMessage != null) {
                        Text(
                            text = validationMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = {
                            onSave(
                                BodyMetricInput(
                                    weightKg = bodyWeightText.toFloat(),
                                    bodyFatPercentage = bodyFatText.toFloatOrNull(),
                                    waistCm = waistText.toFloatOrNull(),
                                    sleepHours = sleepHoursText.toFloatOrNull(),
                                    fatigueScore = fatigueScoreText.toIntOrNull(),
                                    notes = bodyNotes,
                                ),
                            )
                            resetForm()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("身体状态已保存 ✓")
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存身体状态")
                    }
                }
            }
        }
    }
}

// ─── Preset Chips ───────────────────────────────────────────────────────────

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
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────
