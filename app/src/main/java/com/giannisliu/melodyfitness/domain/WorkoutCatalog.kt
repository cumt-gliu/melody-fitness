package com.giannisliu.melodyfitness.domain

data class WorkoutRecordMode(
    val id: String,
    val label: String,
)

object WorkoutCatalog {
    val recordModes = listOf(
        WorkoutRecordMode(id = "strength", label = "力量训练"),
        WorkoutRecordMode(id = "cardio", label = "有氧训练"),
        WorkoutRecordMode(id = "body", label = "身体状态"),
    )

    val strengthTitlePresets = listOf(
        "胸部训练",
        "背部训练",
        "腿部训练",
        "肩部训练",
        "手臂训练",
        "核心训练",
        "全身训练",
        "推训练",
        "拉训练",
        "下肢训练",
        "臀腿训练",
        "功能训练",
        "徒手训练",
        "弹跳训练",
        "扣篮专项",
        "爆发力训练",
    )

    val bodyweightExercisePresets = listOf(
        "俯卧撑",
        "窄距俯卧撑",
        "引体向上",
        "反向划船",
        "徒手深蹲",
        "保加利亚分腿蹲",
        "弓步蹲",
        "弓步跳",
        "深蹲跳",
        "臀桥",
        "单腿臀桥",
        "提踵",
        "平板支撑",
        "登山跑",
    )

    val dunkExercisePresets = listOf(
        "助跑摸高",
        "原地纵跳",
        "箱跳",
        "深蹲跳",
        "连续弹跳",
        "单脚起跳",
        "跨步跳",
        "跳深",
        "落地缓冲",
        "踝弹跳",
        "提膝跳",
        "核心抗旋",
    )

    val cardioActivityPresets = listOf(
        "跑步",
        "骑行",
        "游泳",
        "椭圆机",
        "划船机",
        "跳绳",
        "徒步",
        "爬楼机",
        "篮球",
        "羽毛球",
        "足球",
        "网球",
        "瑜伽",
        "普拉提",
        "HIIT",
        "拳击",
    )
}
