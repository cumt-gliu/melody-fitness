# Melody Fitness

一款 Android 健身记录应用，帮助你追踪力量训练、有氧运动、身体状态和目标进度。

## 功能

- **力量训练记录** — 记录每次训练的动作、组数、重量和次数，支持预设动作模板快速输入
- **有氧训练记录** — 追踪跑步、骑行、游泳等有氧活动的时间、距离和配速
- **身体状态追踪** — 记录体重、体脂、腰围、睡眠和疲劳感等指标
- **目标设置** — 设定体重、训练频率或表现目标，实时显示完成进度
- **训练统计** — 概览本周训练次数、有氧累计、力量最佳重量、体重变化趋势及个性化建议
- **历史记录** — 按时间查看、编辑或删除训练记录
- **本地数据存储** — 基于 Room 数据库，所有数据存储在本地
- **JSON 备份导出** — 一键导出全部数据为 JSON 文件
- **单位切换** — 支持公斤 (kg) 和磅 (lb) 切换显示

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM (ViewModel + Repository) |
| 本地存储 | Room 数据库 |
| 导航 | Navigation Compose |
| 构建工具 | Gradle + Kotlin DSL |

## 开发环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Gradle 8.13
- Android SDK 36
- Kotlin 2.0.21

## 快速开始

```bash
# 克隆项目
git clone https://github.com/your-username/melody-fitness.git

# 打开项目
cd melody-fitness
open -a "Android Studio" .

# 或者在终端中构建
./gradlew assembleDebug
```

## 构建命令

| 命令 | 说明 |
|------|------|
| `./gradlew assembleDebug` | 构建 Debug APK |
| `./gradlew test` | 运行单元测试 |
| `./gradlew lint` | 代码静态检查 |

## 项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/giannisliu/melodyfitness/
│   │   │   ├── data/
│   │   │   │   ├── local/            # Room 数据库、DAO、实体
│   │   │   │   │   ├── dao/
│   │   │   │   │   └── entity/
│   │   │   │   ├── repository/       # 数据仓库层
│   │   │   │   └── settings/         # 设置持久化
│   │   │   ├── domain/               # 业务逻辑（动作目录、进度计算、备份格式化）
│   │   │   ├── ui/                   # Compose UI 和 ViewModel
│   │   │   │   └── theme/
│   │   │   ├── MainActivity.kt
│   │   │   └── MelodyFitnessApplication.kt
│   │   └── AndroidManifest.xml
│   └── test/                         # 单元测试
```

## 测试

项目包含针对 Repository、Domain 层和数据库迁移的单元测试：

```bash
./gradlew testDebugUnitTest
```

## License

MIT
