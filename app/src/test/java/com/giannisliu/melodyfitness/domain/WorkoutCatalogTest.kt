package com.giannisliu.melodyfitness.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCatalogTest {

    @Test
    fun exposes_richer_recording_categories_without_duplicate_labels() {
        assertTrue(WorkoutCatalog.recordModes.map { it.label }.containsAll(listOf("力量训练", "有氧训练", "身体状态")))

        assertTrue(
            WorkoutCatalog.strengthTitlePresets.containsAll(
                listOf("胸部训练", "背部训练", "腿部训练", "肩部训练", "手臂训练", "核心训练", "全身训练"),
            ),
        )
        assertTrue(
            WorkoutCatalog.cardioActivityPresets.containsAll(
                listOf("跑步", "骑行", "游泳", "椭圆机", "划船机", "跳绳", "徒步", "篮球", "羽毛球", "瑜伽"),
            ),
        )

        assertEquals(
            WorkoutCatalog.strengthTitlePresets.size,
            WorkoutCatalog.strengthTitlePresets.distinct().size,
        )
        assertEquals(
            WorkoutCatalog.cardioActivityPresets.size,
            WorkoutCatalog.cardioActivityPresets.distinct().size,
        )
    }

    @Test
    fun exposes_bodyweight_and_dunk_training_presets() {
        assertTrue(
            WorkoutCatalog.strengthTitlePresets.containsAll(
                listOf("徒手训练", "弹跳训练", "扣篮专项", "爆发力训练"),
            ),
        )

        assertTrue(
            WorkoutCatalog.bodyweightExercisePresets.containsAll(
                listOf("俯卧撑", "引体向上", "深蹲跳", "保加利亚分腿蹲", "弓步跳", "提踵"),
            ),
        )
        assertTrue(
            WorkoutCatalog.dunkExercisePresets.containsAll(
                listOf("助跑摸高", "原地纵跳", "箱跳", "深蹲跳", "连续弹跳", "单脚起跳", "落地缓冲", "核心抗旋"),
            ),
        )

        assertEquals(
            WorkoutCatalog.bodyweightExercisePresets.size,
            WorkoutCatalog.bodyweightExercisePresets.distinct().size,
        )
        assertEquals(
            WorkoutCatalog.dunkExercisePresets.size,
            WorkoutCatalog.dunkExercisePresets.distinct().size,
        )
    }
}
