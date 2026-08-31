# ME.md — 双端「ME 个人管理系统」项目速查手册

> **本文件的用途**：让新开的 AI 对话 / 子任务只读这一个文件就能理解两个项目的结构、模块职责与编码规范，直接动手改代码，无需再全量扫描源码。
> **维护约定**：每次改代码后，涉及本文件描述的内容（结构、模块、约定）请同步更新本文；两个项目各自的 README.md 末尾都有「更新日志」章节，用户可见改动务必追加。

---

## 0. 一句话总览

同一产品「ME（个人管理系统，目标·任务·时间·健康）」的两端实现，纯本地存储、无服务器、MIT 协议，UI 全中文：

| 目录 | 端 | 技术栈 | 入口 | 当前版本 |
|---|---|---|---|---|
| `ME PE/` | Android 移动端 | Kotlin + Jetpack Compose (Material 3) | `com.joe.mepe` | v2.4.40 (versionCode 46) |
| `Fangan/` | Windows 桌面端 | .NET 8 WPF (+WinForms 托盘) | 命名空间 `ME` | v2.3.15.0 |

- 两端**数据格式完全兼容**（同一套 JSON 文件名与字段），备份/云同步互通，功能对齐。
- 桌面端仓库：github.com/nailao946/ME；安卓端仓库：github.com/nailao946/ME-PE；云同步数据仓库：**ME-Data**。
- 工作区根目录：`C:\Users\admin\Desktop\ME`（本文与其同目录）。

---

## 1. 双端关系与数据互通（改数据模型前必读）

1. **存储介质**：两端都没有数据库，全部数据是 JSON 文件。PC 端在 `%LocalAppData%\ME\JsonData\*.json`，安卓端在应用 `files/JsonData/*.json`，**文件名、字段名、枚举数字、时间格式必须完全一致**。
2. **JSON 互操作约定**（改动模型字段时必须遵守，否则双端同步会坏）：
   - 字段名用 **PascalCase**；安卓端用 `@SerialName("PascalCase")` 与 C# 属性名对齐。
   - 枚举一律**存数字**（如 GoalColor 红=0…黄=5），两端同一数字代表同一含义。
   - 时间：`LocalDateTime` 序列化为 `yyyy-MM-ddTHH:mm:ss`（无时区），兼容 C# System.Text.Json；时长用 TimeSpan 的 `c` 格式（如 `01:30:00`）。
   - 安卓端 `Json` 配置：`ignoreUnknownKeys=true`（两端字段不同步时不会崩）。
3. **备份互通**：PC 端备份是目录（`me_backup_*.db` 内含 json）→ 压缩成 zip 后在安卓「设置 → 导入备份」导入；安卓「导出备份」生成 `me_backup_时间.zip` → 解压覆盖 PC 端 `JsonData` 目录即可回写。
4. **云同步**：GitHub（Device Flow 账号授权，自动建私有仓 ME-Data）/ Gitee / WebDAV（坚果云 `https://dav.jianguoyun.com/dav/`）三种后端，行为完全一致：先上传后下载、逐文件比对 sha 防覆盖、启动自动同步、状态球。Token 加密存本机（PC 用 DPAPI，安卓存本机文件），不上传。
5. **共享设置键**（双端互通）：喝水目标 `HealthWaterGoal`、番茄钟 6 项参数、时间统计标签范围 `StatsIncludedTags`、AI 供应商文件 `ai_providers.json`（`ApiFormat` 0=OpenAI 兼容 / 1=Anthropic，两端一致）。心情五档 1~5（旧 0~3 记录显示时自动换算）。
6. **同步文件清单**：PC 端权威清单在 `Fangan/ME/Services/SyncService.cs` 的 `DataFiles`（约 12 个：tasks、goals、tags、time_tags、time_records、task_completions、focus_sessions、settings、visions、reviews、health_records、water_containers）；medications / exercise_items / ai_providers / 自定义模块等未纳入同步。安卓端 `JsonStore.allFiles()` 会把目录里所有 json 一并上传，但仅这些文件双向比对。
7. **版本对应**：PC 2.3.x ↔ 安卓 2.4.x（功能对齐）。两端 README 更新日志需一起看，多数改动是「一端改、另一端同步改」。

---

## 2. Fangan — Windows 桌面端

### 2.1 技术栈与构建

- 方案 `Fangan.slnx`，单项目 `ME/ME.csproj`（根命名空间 `ME`）。
- **net8.0-windows**，`UseWPF` + `UseWindowsForms`（托盘 NotifyIcon 用 WinForms）。
- 唯一 UI 包：**WPF-UI (lepoco) 3.0.5**（Fluent 主题；v2.0.0 从 ModernWpf 迁移而来，README 里的 ModernWpf 字样已过时）。
- csproj 特殊项（勿删改）：`<Nullable>disable</Nullable>`、`<ImplicitUsings>disable</ImplicitUsings>`（文件需显式 using）、`<DisableHardwareAcceleration>true</DisableHardwareAcceleration>`、`<SatelliteResourceLanguages>zh-Hans</SatelliteResourceLanguages>`。
- 版本号在 `ME/ME.csproj` 的 `<Version>`（当前 2.3.15.0）。
- 构建/运行/发布：
  ```bash
  dotnet build ME/ME.csproj
  dotnet run --project ME          # 启动 WPF 应用（仅 Windows）
  dotnet publish "ME\ME.csproj" -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
  ```
- 仓库无测试、无 lint 配置；bin/obj/.vs 不提交。

### 2.2 目录结构（`Fangan/ME/`）

| 目录 | 职责 |
|---|---|
| `Models/` | POCO 数据模型，一个实体一个文件：Goal、GoalTag、TaskItem、TaskCompletionRecord、TimeTag、TimeRecord、FocusSession、HealthRecord、MedicationRecord、WaterContainer、ExerciseItem、Vision、Review、CustomModule(+Field/Record/CustomDashboard 及静态仓库)、AiProvider、AppSettings(+SettingsKeys)。实体枚举也在此（TaskType、RecurringPattern、QuantitativeMode、GoalColor、GoalTimeFrame、DecomposeMode、MedicationType/Unit/Frequency、ReviewType、TimerMode、AppTheme、AiApiFormat）。 |
| `Data/` | 静态仓库类 + `DatabaseHelper.cs`（内含静态 `JsonStore`）。每实体一个 `XxxRepository`，全部走 `JsonStore.LoadWithCache<T>(fileName)` / `JsonStore.Save`。 |
| `Services/` | 应用服务：托盘、提醒、计时器、备份、LLM、主题、云同步、小米导入、DPAPI SecureStore 等（见 2.3）。 |
| `Core/` | MVVM 基础设施：`ViewModelBase`、`RelayCommand`、`EventAggregator`、`NavigationService`。 |
| `ViewModels/` | Calendar、FocusTimer、Goals、MainWindow、Map、RecycleBin、Review、Settings、Tasks 九个 ViewModel（继承 ViewModelBase）。 |
| `Views/` | XAML 视图 + 对话框（MainWindow 挂载 8 个主视图，另有大量编辑/确认对话框）。 |
| `Converters/` | GoalTagColorConverter、QuantitativeProgressConverter。 |
| `Resources/Styles.xaml` | 全局样式/主题资源（DynamicResource 切换深浅色）。 |
| `Properties/` | 发布配置（FolderProfile*.pubxml）、资源。 |

### 2.3 关键模块说明（Services / 架构要点）

**导航**：`MainWindow.xaml.cs` 的 `UpdateView(index)` 按索引切换 8 个主视图：
0 任务列表 TasksView · 1 目标管理 GoalsView · 2 日历视图 CalendarView · 3 定期盘点 ReviewView · 4 时间追踪 TimeTrackView · 5 健康 HealthView · 6 自定义模块 CustomModulesView · 7 设置 SettingsView。

**存储架构（重要）**：
- 无数据库；静态 `JsonStore`（`Data/DatabaseHelper.cs`）读写 `%LocalAppData%\ME\JsonData\*.json`。
- 读走 `LoadWithCache<T>`（**2 秒内存缓存**）；`JsonStore.Save` 会失效该文件缓存——**所有写操作必须经 JsonStore.Save**；read-modify-write 时先失效或重读，避免拿到旧缓存。
- 新增实体的标准流程：`Models/` 建模型 → `Data/` 建静态仓库 → 接入 Views/Services。
- API Key 用 DPAPI `SecureStore` 加密，**禁止明文存 JSON**；普通设置走 `SettingsRepository`。

**Services 清单（一句话职责）**：
- `AppNotifier` — 托盘气泡提示；借用 MainWindow 的 NotifyIcon（不新建常驻图标），临时兜底图标 12 秒自动隐藏。
- `BackupService` — 备份/恢复到 `%LocalAppData%\ME\`；健康/用药数据在备份内，visions/reviews 不在（截至 v1.9.24）。
- `GitHubSyncService` — 云同步三后端（GitHub/Gitee/WebDAV，内部 `ICloudStore` 接口 + GitHubStore/GiteeStore/WebDavStore 实现）、GitHub Device Flow 登录（动态放宽轮询间隔）、令牌自动续期（refresh_token）、DoH 备用 DNS。
- `SyncService` — 同步统一入口与 `DataFiles` 清单、导出/导入。
- `SyncStatusService` — 左下角同步状态球（SyncBallState）。
- `LlmService` — AI 分析；任意 OpenAI 兼容服务 + Anthropic（/v1/messages）；默认 prompt 可编辑持久化，可恢复默认。
- `TaskService` / `GoalService` / `ReviewService` — 任务、目标、盘点业务规则。
- `PomodoroService` / `SharedTimerService` / `SharedPomodoroService` / `FocusTimerService` / `TimeTimerService` — 番茄钟与各类计时引擎（共享引擎被多个窗口复用）。
- `IdleTimeService` — 自动补插「闲时」计时记录。
- `MedicationReminderService` — 用药到点提醒。
- `SoundService` — 完成/专注提示音。
- `ThemeService` — 浅色/深色/跟随系统运行时切换（DynamicResource）。
- `UpdateCheckService` — 对比 GitHub Releases 检查更新。
- `SecureStore` — DPAPI 加密存储。
- `XiaomiImportService` — 解析小米隐私中心导出 zip（仅睡眠 + 体重）。
- `VisionService` — 目标地图/愿景数据。
- `TimeStatsHelper` — 时间统计口径（供时间页/盘点共用）。

### 2.4 编码规范与注意事项

- **架构是 MVVM + code-behind 混合**，很多逻辑在 code-behind（如 `HealthView.xaml.cs` 约 3000 行）——**改哪个文件就沿用那个文件的风格，不要强行重构为纯 MVVM**。
- C#：Nullable/ImplicitUsings 关闭，显式 using；不写与周边不符的可空注解。
- **UI 文案一律中文**（README、弹窗、按钮）；提交风格 `vX.Y.Z: 中文摘要`。
- 新样式必须用 **DynamicResource** 引用才跟随主题切换。
- 单一托盘图标约束（见 AppNotifier）；不要新增常驻 NotifyIcon。
- 改健康模块 / 备份范围 / 托盘通知前，先读 `Fangan/README.md` 功能说明（这些区域反复改过，README 记录的是期望行为）。
- 改完用户可见功能：升 `<Version>` + 在 `Fangan/README.md` 更新日志追加说明。

---

## 3. ME PE — 安卓端

### 3.1 技术栈与构建

- Gradle 根项目名 "ME PE"，模块 `:app`；namespace / applicationId = `com.joe.mepe`。
- Kotlin + Jetpack Compose (Material 3)，Compose BOM 2024.02.01，`kotlinCompilerExtensionVersion = "1.5.4"`；kotlinx-serialization-json 1.6.2；OkHttp 4.12.0 + okhttp-dnsoverhttps（DoH）；**无第三方数据库**。
- minSdk 26（Android 8.0）/ target 34 / compile 34，JDK 17。
- 版本：`versionName = "2.4.40"`，`versionCode = 46`（在 `app/build.gradle.kts`）。
- Maven 仓库：阿里云镜像优先（`maven.aliyun.com`），官方源兜底。
- 构建：
  ```bash
  ./gradlew assembleDebug     # 输出 app/build/outputs/apk/debug/app-debug.apk
  ./gradlew installDebug      # 连接设备直接安装
  ```
- Manifest 组件：`MEApp`（Application）、`MainActivity`、`data.TimerNotificationService`（前台服务 specialUse，标签计时通知）、`notify.AlarmReceiver`、`notify.BootReceiver`（开机重排用药闹钟）。权限：POST_NOTIFICATIONS、RECEIVE_BOOT_COMPLETED、INTERNET、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE。

### 3.2 目录结构（`app/src/main/java/com/joe/mepe/`）

| 路径 | 职责 |
|---|---|
| `MEApp.kt` / `MainActivity.kt` | 应用入口、通知渠道、JsonStore 初始化。 |
| `data/Models.kt` | **全部数据模型**（与桌面端字段一致）+ 枚举常量对象（见 3.4）。 |
| `data/Serializers.kt` | `LocalDateTimeSerializer` / `DurationSerializer`（兼容桌面端时间/时长格式）。 |
| `data/JsonStore.kt` | `JsonStore`（本地 JSON 读写，文件名格式与 PC 完全相同）+ `DataBus.rev` 全局版本号。 |
| `data/Repos.kt` | 静态仓库对象 `Repos`：所有数据的增删改查，写后 `DataBus.bump()`。 |
| `data/TaskLogic.kt` | 任务出现/完成/进度规则（与桌面端 TaskService 对应）。 |
| `data/BackupManager.kt` | zip 备份导出/导入。 |
| `data/Sync.kt` | 云同步全实现：`SyncConfig`、`CloudSync`、`GitBackend`/`WebDavBackend`、`GitHubLogin`（Device Flow）、`DnsFallback`（系统 DNS 失败时用阿里 223.5.5.5 加密 DNS 兜底）。 |
| `data/TimerNotificationService.kt` | 标签计时前台服务（通知栏实时走秒 + 停止按钮）。 |
| `data/UpdateChecker.kt` | 对比 GitHub Releases 检查更新。 |
| `notify/ReminderScheduler.kt` | AlarmManager 每日用药提醒 + 开机重排（AlarmReceiver/BootReceiver）。 |
| `ai/LlmService.kt` | AI 分析：OpenAI 兼容 + Anthropic 两种格式。 |
| `ui/AppNav.kt` | `Routes` 常量、`AppRoot()` 导航（底部 5 主 Tab + 返回栈 + 转场动画）。 |
| `ui/theme/Theme.kt` | `METheme`、6 种强调色 `Accents`、图标颜色、`parseHexColor`/`colorToHex`、`LocalIconColor`。 |
| `ui/Charts.kt` | Canvas 图表：LineChart、BarChart、DonutChart、ProgressRing。 |
| `ui/Components.kt` | 通用组件：ScreenHeader、SectionCard、RoundedProgressBar、Segmented、Stepper、Time/DatePickerDialog 等。 |
| `ui/SyncStatus.kt` | `SyncStatusBus` + 顶栏同步状态球 `SyncBall`。 |
| `ui/tasks/` | TasksScreen（任务列表、日期条、左滑操作、详情弹窗、打卡图）、TaskEditDialog。 |
| `ui/goals/` | GoalsScreen（目标树、子目标、拖动排序、量化弹窗、详情页）、GoalEditDialog、TagManagerDialog。 |
| `ui/calendar/` | CalendarScreen（月历完成率色块）。 |
| `ui/timetrack/` | TimeTrackScreen + `PomodoroEngine`（番茄钟状态机）、标签计时、统计弹窗。 |
| `ui/health/` | HealthScreen：9 个子页签（总览/睡眠/身体/喝水/心情/尿酸/锻炼/久坐/用药 + 对比/AI 分析）。 |
| `ui/map/` | MapScreen 目标树形总览。 |
| `ui/review/` | ReviewScreen 盘点（周/月统计 + 时间统计）。 |
| `ui/modules/` | ModulesScreen 自定义模块（字段类型 number/text/time/bool/select，趋势图+历史）。 |
| `ui/settings/` | SettingsScreen：外观/健康目标/云同步/备份/云同步/AI/模块/关于各子页。 |

### 3.3 架构要点（Compose 数据流）

- **存储**：`JsonStore`（`data/JsonStore.kt`）读写 `files/JsonData/*.json`，`Json { ignoreUnknownKeys=true; encodeDefaults=true; prettyPrint=true }`。
- **状态刷新**：`DataBus.rev`（mutableStateOf）——仓库任何写入后 `DataBus.bump()`，Compose 界面通过读取 `rev` 触发重组。**写数据必须走 Repos（或直接 bump），否则界面不刷新**。
- **导航**：`Routes` 字符串路由；底部主 Tab = 任务/目标/日历/时间/健康（`Routes.mainTabs`），右上角 QuickLinks = 地图/盘点/设置；`AppRoot()` 维护 backStack，系统返回逐级回退（设置→子页→主界面）。
- **主题**：`METheme` + `resolveAccent`；颜色选择统一全色 HSV 调色盘（v2.4.0 起）。
- 任务/目标列表：手动拖动排序（虚影 + 松手落位，v2.4.19 机制）、左滑编辑/删除（v2.4.23 起为纯图标）、完成仅通过左侧勾选圈。

### 3.4 数据模型与枚举（`data/Models.kt`，数值与 PC 端一致）

模型：TaskItem、Goal、GoalTag、TaskCompletionRecord、TimeTag、TimeRecord、HealthRecord、WaterContainer、ExerciseItem、MedicationRecord、Vision、Review、FocusSession、AppSetting、AiProvider、CustomModuleField、CustomModuleRecord、CustomModule。

枚举常量对象：
- `TaskTypes`：ONE_TIME=0 一次性 / PERIODIC=1 周期 / RECURRING=2 循环 / QUANTITATIVE=3 量化
- `RecPatterns`：DAILY=0 每日 / WEEKDAY=1 工作日 / WEEKEND=2 周末 / WEEKLY=3 每周 / MONTHLY=4 每月 / INTERVAL=5 间隔 / CUSTOM=6 自定义
- `QuantModes`：ACCUMULATE=0 累加 / UPDATE=1 设为该值
- `GoalColors`：RED=0 GREEN=1 BLUE=2 PINK=3 GRAY=4 YELLOW=5
- `TimeFrames`：SHORT=0 短期 / LONG=1 长期 / INSPIRATION=2 灵感
- `HealthTypes`：健康记录类型常量（睡眠/体重/喝水/心情/尿酸/锻炼/久坐/用药）
- `MedTypes`：CAPSULE=0 TABLET=1 LIQUID=2 TOPICAL=3 INHALER=4 POWDER=5 INJECTION=6；单位 0=ml 1=mg 2=g 3=mcg 4=%；频率 0=每天 1=每隔N天 2=每周特定日 3=循环定时 4=按需

### 3.5 编码规范与注意事项

- **@SerialName("PascalCase")** 对齐 C# 属性名；枚举存数字；时间 `yyyy-MM-ddTHH:mm:ss`、时长 TimeSpan `c` 格式（见 1.2）。
- UI 文案中文；`strings.xml` 应用名为「ME（个人管理系统）」。
- 版本号在 `app/build.gradle.kts`（versionName/versionCode），**关于页自动读取**，不要硬编码。
- 改完用户可见功能：升版本 + 在 `ME PE/README.md` 更新日志追加（README 完整记录了 v2.0.0 → v2.4.40 全部改动，是功能行为的第一手参考）。
- 常用交互历史（避免重复实现/回退）：左滑操作已改 5 版（当前纯图标版 v2.4.23）；拖动排序 v2.4.19 起为「虚影+松手落位」；番茄钟卡片 v2.4.21 起紧凑布局。**改这些交互前先读 README 对应版本记录**。

---

## 4. 双端互通的硬约束（改模型/同步前逐条对照）

1. 新增/改名模型字段 → **两端同时改**，PascalCase 一致；安卓加 `@SerialName`。
2. 新增枚举值 → 追加到末尾（用新数字），不要重排已有数字。
3. 新增数据文件 → 若要同步，同时加入 PC `SyncService.DataFiles`；安卓 `JsonStore` 自动按目录文件上传，但只有清单内的文件才双向比对/防覆盖。
4. 时间/时长格式变更会破坏历史数据与双端解析——默认不要改。
5. 改云同步行为 → 两端保持同一逻辑（PC `GitHubSyncService.cs` ↔ 安卓 `data/Sync.kt`），README 里有大量两端同步修复的历史（409 WebDAV、Gitee sha、401 续期、DoH 等），实现前先看。

---

## 5. 常见修改任务 → 文件速查表

| 想改什么 | PC 端 (Fangan/ME/) | 安卓端 (ME PE/…/com/joe/mepe/) |
|---|---|---|
| 任务规则/统计口径 | Services/TaskService.cs、Data/TaskRepository.cs、Services/TimeStatsHelper.cs | data/TaskLogic.kt、data/Repos.kt |
| 数据模型字段 | Models/TaskItem.cs 等 + 各仓库 | data/Models.kt（+ Serializers.kt） |
| 云同步 | Services/GitHubSyncService.cs、Services/SyncService.cs | data/Sync.kt |
| AI 分析 | Services/LlmService.cs | ai/LlmService.kt |
| 主题/外观 | Resources/Styles.xaml、Services/ThemeService.cs | ui/theme/Theme.kt |
| 健康模块 | Views/HealthView.xaml(.cs)（code-behind 极重） | ui/health/HealthScreen.kt |
| 任务/目标 UI | Views/TasksView / GoalsView + 对应 ViewModel | ui/tasks/、ui/goals/ |
| 计时/番茄钟 | Services/PomodoroService.cs、SharedTimerService.cs | ui/timetrack/TimeTrackScreen.kt (PomodoroEngine) |
| 备份/导入 | Services/BackupService.cs | data/BackupManager.kt |
| 用药提醒 | Services/MedicationReminderService.cs | notify/ReminderScheduler.kt |
| 版本检查 | Services/UpdateCheckService.cs | data/UpdateChecker.kt |
| 自定义模块 | Models/CustomModule.cs、Views/CustomModulesView.xaml.cs | ui/modules/ModulesScreen.kt |
| 同步状态球 | Services/SyncStatusService.cs、MainWindow.xaml.cs | ui/SyncStatus.kt |
| 新增实体 | Models/ + Data/ 新仓库 + 视图接入 | Models.kt + Repos.kt + UI |

---

## 6. 版本与文档约定

- 版本号位置：PC `ME/ME.csproj` `<Version>`；安卓 `app/build.gradle.kts` `versionName/versionCode`。
- 提交信息：`vX.Y.Z: 中文摘要`。
- 更新日志：各自 README.md 末尾追加（**维护用户可见行为的权威记录**）。
- 本文件（ME.md）：结构/模块/约定有变时同步更新；两端 AGENTS.md / 文档与本文件冲突时以本文为准并顺手修正。
