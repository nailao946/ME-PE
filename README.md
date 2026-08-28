# 目标地图 PE (ME PE)

**目标地图** 的 Android 版本 —— 个人目标管理 & 健康追踪工具，纯本地存储，与 Windows 桌面版（WPF）数据格式完全兼容。

## 功能（与桌面版对齐）

| 模块 | 功能 |
|------|------|
| 📋 任务 | 一次性/周期/量化任务、日期条筛选、标签过滤、打卡（每日多次）、**▲▼ 同级手动排序**、子任务 |
| 🎯 目标 | 短期/长期/灵感分类、标签系统、颜色、父子层级、量化目标、进度自动计算 |
| 📅 日历 | 月视图完成率色块、当日任务详情、月度打卡率/待完成/连续全勤统计 |
| ⏱️ 时间 | 标签一键计时、今日时间线、本周/月用时分布环图、标签管理 |
| 💚 健康 | 睡眠 / 体重BMI / 喝水容器 / 心情 / 尿酸(男女正常范围) / 锻炼项目 / 久坐计数 / 用药记录 |
| 📊 对比 | 两参数叠加趋势 + AI 分析相关性（OpenAI 兼容接口） |
| 🗺️ 地图 | 目标树形总览、进度环、整体进度 |
| 📝 盘点 | 周/月完成率趋势、目标进度、盘点笔记 |
| ⚙️ 设置 | 浅色/深色/跟随系统、6 种强调色、喝水/活动目标、**备份导出导入**、AI 供应商 |

## 数据与桌面版互通

- 存储格式：`files/JsonData/*.json`，字段名/枚举值/时间格式与桌面端 `%LocalAppData%\ME\JsonData` 完全一致。
- 桌面端备份是目录（`me_backup_*.db` 目录内含 `*.json`）→ 把目录 zip 后在手机「设置 → 导入备份」即可导入。
- 手机端「导出备份」生成 `me_backup_时间.zip` → 解压覆盖桌面端 `JsonData` 目录即可同步回电脑。

## 构建

需要 JDK 17 与 Android SDK 34：

```bash
./gradlew assembleDebug     # 输出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # 连接设备后直接安装
```

- 最低支持 Android 8.0（API 26），目标 Android 14（API 34）。
- 技术栈：Kotlin + Jetpack Compose (Material 3) + kotlinx.serialization + OkHttp，无第三方数据库。
- 用药提醒通过 AlarmManager 每日循环通知，开机自启重排（`BootReceiver`）。

## 技术说明

- JSON 序列化使用 `@SerialName("PascalCase")` 与 C# 属性名对齐；枚举以数字存储（如 GoalColor: 红=0 … 黄=5）。
- `LocalDateTime` 序列化为 `yyyy-MM-ddTHH:mm:ss`（无时区），兼容 C# `System.Text.Json` 默认格式与 TimeSpan 的 `c` 格式。
- `DataBus.rev` 全局版本号：任何仓库写入后 +1，Compose 通过它触发重组刷新。

## 目录结构

```
app/src/main/java/com/joe/mepe/
├── MEApp.kt / MainActivity.kt      # 应用入口、通知渠道
├── data/
│   ├── Models.kt                   # 全部数据模型（与桌面端字段一致）
│   ├── Serializers.kt              # DateTime/TimeSpan 兼容序列化器
│   ├── JsonStore.kt / Repos.kt     # JSON 存储 + 仓库层
│   ├── TaskLogic.kt                # 任务出现/完成/进度规则
│   └── BackupManager.kt            # zip 备份导出导入
├── notify/                         # 用药提醒闹钟 + 开机重排
├── ai/LlmService.kt                # OpenAI 兼容 chat 调用
└── ui/
    ├── theme/ Charts / Components  # 主题、Canvas 图表（折线/柱状/环形/进度环）、通用组件
    ├── AppNav.kt                   # 底部 5 Tab + 顶部地图/盘点/设置入口
    ├── tasks/ goals/ calendar/ timetrack/
    ├── health/                     # 8 个健康子页签 + 对比 + AI 分析
    └── map/ review/ settings/
```
