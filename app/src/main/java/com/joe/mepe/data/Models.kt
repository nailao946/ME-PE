package com.joe.mepe.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDateTime

// ============ 枚举常量（桌面端以数字存储，这里保持一致的数值） ============

/** TaskType: 0=一次性 1=周期性 2=循环 3=量化 */
object TaskTypes { const val ONE_TIME = 0; const val PERIODIC = 1; const val RECURRING = 2; const val QUANTITATIVE = 3 }

/** RecurringPattern: 0=每日 1=工作日 2=周末 3=每周 4=每月 5=间隔 6=自定义 */
object RecPatterns { const val DAILY = 0; const val WEEKDAY = 1; const val WEEKEND = 2; const val WEEKLY = 3; const val MONTHLY = 4; const val INTERVAL = 5; const val CUSTOM = 6 }

/** QuantitativeMode: 0=累加 1=更新 */
object QuantModes { const val ACCUMULATE = 0; const val UPDATE = 1 }

/** GoalColor: 0=红 1=绿 2=蓝 3=粉 4=灰 5=黄 */
object GoalColors { const val RED = 0; const val GREEN = 1; const val BLUE = 2; const val PINK = 3; const val GRAY = 4; const val YELLOW = 5 }

/** GoalTimeFrame: 0=短期 1=长期 2=灵感 */
object TimeFrames { const val SHORT = 0; const val LONG = 1; const val INSPIRATION = 2 }

// ============ 模型（字段名与桌面端 JSON 完全一致） ============

@Serializable
data class TaskItem(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Title") var title: String = "",
    @SerialName("Description") var description: String? = null,
    @SerialName("Type") var type: Int = TaskTypes.ONE_TIME,
    @SerialName("GoalId") var goalId: Int? = null,
    @SerialName("ParentTaskId") var parentTaskId: Int? = null,
    @SerialName("StartDate") @Serializable(with = LocalDateTimeSerializer::class) var startDate: LocalDateTime? = null,
    @SerialName("EndDate") @Serializable(with = LocalDateTimeSerializer::class) var endDate: LocalDateTime? = null,
    @SerialName("IsCompleted") var isCompleted: Boolean = false,
    @SerialName("CompletedAt") @Serializable(with = LocalDateTimeSerializer::class) var completedAt: LocalDateTime? = null,
    @SerialName("IsDeleted") var isDeleted: Boolean = false,
    @SerialName("DeletedAt") @Serializable(with = LocalDateTimeSerializer::class) var deletedAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("UpdatedAt") @Serializable(with = LocalDateTimeSerializer::class) var updatedAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("Priority") var priority: Int = 0,
    @SerialName("RecurringPattern") var recurringPattern: Int? = null,
    @SerialName("RecurringInterval") var recurringInterval: Int? = null,
    @SerialName("RecurringDaysOfWeek") var recurringDaysOfWeek: String? = null,
    @SerialName("RecurringDayOfMonth") var recurringDayOfMonth: Int? = null,
    @SerialName("IsLastDayOfMonth") var isLastDayOfMonth: Boolean = false,
    @SerialName("RecurringTimesPerDay") var recurringTimesPerDay: Int? = null,
    @SerialName("RecurringTimesPerWeek") var recurringTimesPerWeek: Int? = null,
    @SerialName("RecurringCurrentCount") var recurringCurrentCount: Int? = null,
    @SerialName("RecurringTargetCount") var recurringTargetCount: Int? = null,
    @SerialName("IsRecurringCompleted") var isRecurringCompleted: Boolean = false,
    @SerialName("LastCompletedDate") @Serializable(with = LocalDateTimeSerializer::class) var lastCompletedDate: LocalDateTime? = null,
    @SerialName("QuantitativeMode") var quantitativeMode: Int? = null,
    @SerialName("QuantitativeStart") var quantitativeStart: Double? = null,
    @SerialName("QuantitativeTarget") var quantitativeTarget: Double? = null,
    @SerialName("QuantitativeCurrent") var quantitativeCurrent: Double? = null,
    @SerialName("QuantitativeUnit") var quantitativeUnit: String? = null,
    @SerialName("QuantitativeDailyMin") var quantitativeDailyMin: Double? = null,
    @SerialName("CountTowardsParent") var countTowardsParent: Boolean = false,
    @SerialName("SortOrder") var sortOrder: Int = 0,
    @SerialName("TimeTagId") var timeTagId: Int? = null,
)

@Serializable
data class Goal(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("Description") var description: String? = null,
    @SerialName("Color") var color: Int = GoalColors.BLUE,
    /** 自定义颜色（扩展字段，两端约定：非空时优先于枚举 Color） */
    @SerialName("ColorHex") var colorHex: String? = null,
    @SerialName("TimeFrame") var timeFrame: Int = TimeFrames.SHORT,
    @SerialName("ParentId") var parentId: Int? = null,
    @SerialName("StartDate") @Serializable(with = LocalDateTimeSerializer::class) var startDate: LocalDateTime? = null,
    @SerialName("EndDate") @Serializable(with = LocalDateTimeSerializer::class) var endDate: LocalDateTime? = null,
    @SerialName("Progress") var progress: Double = 0.0,
    @SerialName("IsArchived") var isArchived: Boolean = false,
    @SerialName("IsLocked") var isLocked: Boolean = false,
    @SerialName("IsDeleted") var isDeleted: Boolean = false,
    @SerialName("DeletedAt") @Serializable(with = LocalDateTimeSerializer::class) var deletedAt: LocalDateTime? = null,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("UpdatedAt") @Serializable(with = LocalDateTimeSerializer::class) var updatedAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("Notes") var notes: String? = null,
    @SerialName("TagId") var tagId: Int? = null,
    @SerialName("QuantitativeMode") var quantitativeMode: Int? = null,
    @SerialName("QuantitativeStart") var quantitativeStart: Double? = null,
    @SerialName("QuantitativeTarget") var quantitativeTarget: Double? = null,
    @SerialName("QuantitativeCurrent") var quantitativeCurrent: Double? = null,
    @SerialName("QuantitativeUnit") var quantitativeUnit: String? = null,
    @SerialName("SortOrder") var sortOrder: Int = 0,
)

@Serializable
data class GoalTag(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("Color") var color: String = "#4F6EF7",
    @SerialName("SortOrder") var sortOrder: Int = 0,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
)

@Serializable
data class TaskCompletionRecord(
    @SerialName("Id") var id: Int = 0,
    @SerialName("TaskId") var taskId: Int = 0,
    @SerialName("Date") var date: String = "",
    @SerialName("CompletedAt") @Serializable(with = LocalDateTimeSerializer::class) var completedAt: LocalDateTime = LocalDateTime.MIN,
)

@Serializable
data class TimeTag(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("Color") var color: String = "#4F6EF7",
    @SerialName("Notes") var notes: String? = null,
    @SerialName("SortOrder") var sortOrder: Int = 0,
    @SerialName("IsPreset") var isPreset: Boolean = false,
)

@Serializable
data class TimeRecord(
    @SerialName("Id") var id: Int = 0,
    @SerialName("TagId") var tagId: Int = 0,
    @SerialName("StartTime") @Serializable(with = LocalDateTimeSerializer::class) var startTime: LocalDateTime = LocalDateTime.MIN,
    @SerialName("EndTime") @Serializable(with = LocalDateTimeSerializer::class) var endTime: LocalDateTime? = null,
    @SerialName("Date") var date: String = "",
    @SerialName("Note") var note: String? = null,
) {
    fun minutes(): Long {
        val end = endTime ?: return 0
        return java.time.Duration.between(startTime, end).toMinutes()
    }
}

@Serializable
data class HealthRecord(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Type") var type: String = "",
    @SerialName("Date") var date: String = "",
    @SerialName("Value") var value: Double = 0.0,
    @SerialName("Detail") var detail: String? = null,
    @SerialName("Note") var note: String? = null,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
)

/** 健康数据类型常量（与桌面端一致） */
object HealthTypes {
    const val SLEEP = "sleep"
    const val WEIGHT = "weight"
    const val WATER = "water"
    const val MOOD = "mood"
    const val URIC_ACID = "uric_acid"
    const val EXERCISE = "exercise"
    const val SEDENTARY = "sedentary"
    const val MEDICATION = "medication"
}

@Serializable
data class WaterContainer(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("CapacityMl") var capacityMl: Double = 250.0,
    @SerialName("IsBuiltIn") var isBuiltIn: Boolean = false,
)

@Serializable
data class ExerciseItem(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("TargetValue") var targetValue: Double = 0.0,
    @SerialName("Unit") var unit: String = "次",
    /** daily=每日 everyOther=隔日 weekly=每周指定几天 */
    @SerialName("Frequency") var frequency: String = "daily",
    /** weekly 时：每周第几天（1=周一 … 7=周日），逗号分隔 */
    @SerialName("WeeklyDays") var weeklyDays: String? = null,
    @SerialName("Category") var category: String? = null,
    @SerialName("SortOrder") var sortOrder: Int = 0,
    @SerialName("Note") var note: String? = null,
    @SerialName("IsDeleted") var isDeleted: Boolean = false,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
)

/** MedicationType: 0=胶囊 1=药片 2=液体 3=外用 4=吸入 5=粉末 6=注射 */
object MedTypes { const val CAPSULE = 0; const val TABLET = 1; const val LIQUID = 2; const val TOPICAL = 3; const val INHALER = 4; const val POWDER = 5; const val INJECTION = 6 }
fun medTypeName(t: Int): String = when (t) {
    MedTypes.CAPSULE -> "胶囊"; MedTypes.TABLET -> "药片"; MedTypes.LIQUID -> "液体"; MedTypes.TOPICAL -> "外用"
    MedTypes.INHALER -> "吸入"; MedTypes.POWDER -> "粉末"; MedTypes.INJECTION -> "注射"; else -> "其他"
}
/** MedicationUnit: 0=ml 1=mg 2=g 3=mcg 4=% */
fun medUnitName(u: Int): String = when (u) { 0 -> "ml"; 1 -> "mg"; 2 -> "g"; 3 -> "mcg"; 4 -> "%"; else -> "" }
/** MedicationFrequency: 0=每天 1=每隔N天 2=每周特定日期 3=循环定时 4=按需 */
fun medFreqName(f: Int): String = when (f) { 0 -> "每天"; 1 -> "每隔N天"; 2 -> "每周特定日"; 3 -> "循环定时"; 4 -> "按需"; else -> "" }

@Serializable
data class MedicationRecord(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("Type") var type: Int = MedTypes.TABLET,
    @SerialName("SpecValue") var specValue: Double = 0.0,
    @SerialName("Unit") var unit: Int = 1,
    @SerialName("Frequency") var frequency: Int = 0,
    @SerialName("FrequencyN") var frequencyN: Int = 1,
    @SerialName("WeeklyDays") var weeklyDays: String? = null,
    /** 服药时间点，逗号分隔 "08:00,12:30" */
    @SerialName("Times") var times: String = "08:00",
    @SerialName("StartDate") @Serializable(with = LocalDateTimeSerializer::class) var startDate: LocalDateTime? = null,
    @SerialName("EndDate") @Serializable(with = LocalDateTimeSerializer::class) var endDate: LocalDateTime? = null,
    @SerialName("Note") var note: String? = null,
    @SerialName("Remind") var remind: Boolean = false,
    @SerialName("IsDeleted") var isDeleted: Boolean = false,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
) {
    fun timeList(): List<String> = times.split(',').map { it.trim() }.filter { it.contains(':') }
}

@Serializable
data class Vision(
    @SerialName("Id") var id: Int = 0,
    @SerialName("CareerVision") var careerVision: String? = null,
    @SerialName("FinanceVision") var financeVision: String? = null,
    @SerialName("HealthVision") var healthVision: String? = null,
    @SerialName("FamilyVision") var familyVision: String? = null,
    @SerialName("SocialVision") var socialVision: String? = null,
    @SerialName("LearningVision") var learningVision: String? = null,
    @SerialName("LeisureVision") var leisureVision: String? = null,
    @SerialName("SpiritualVision") var spiritualVision: String? = null,
    @SerialName("LifeScene") var lifeScene: String? = null,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("UpdatedAt") @Serializable(with = LocalDateTimeSerializer::class) var updatedAt: LocalDateTime = LocalDateTime.MIN,
)

@Serializable
data class Review(
    @SerialName("Id") var id: Int = 0,
    /** 0=周盘点 1=月盘点 2=目标关闭 */
    @SerialName("Type") var type: Int = 0,
    @SerialName("GoalId") var goalId: Int? = null,
    @SerialName("ReviewDate") @Serializable(with = LocalDateTimeSerializer::class) var reviewDate: LocalDateTime = LocalDateTime.MIN,
    @SerialName("CompletionRate") var completionRate: Double = 0.0,
    @SerialName("DelayRatio") var delayRatio: Double = 0.0,
    @SerialName("TimeImbalance") var timeImbalance: String? = null,
    @SerialName("DecomposeIssues") var decomposeIssues: String? = null,
    @SerialName("OptimizationSuggestions") var optimizationSuggestions: String? = null,
    @SerialName("SuccessReasons") var successReasons: String? = null,
    @SerialName("FailureReasons") var failureReasons: String? = null,
    @SerialName("Strengths") var strengths: String? = null,
    @SerialName("Weaknesses") var weaknesses: String? = null,
    @SerialName("PersonalNotes") var personalNotes: String? = null,
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
)

@Serializable
data class FocusSession(
    @SerialName("Id") var id: Int = 0,
    @SerialName("GoalId") var goalId: Int? = null,
    @SerialName("TaskId") var taskId: Int? = null,
    /** 0=秒表 1=倒计时 */
    @SerialName("Mode") var mode: Int = 0,
    @SerialName("Duration") @Serializable(with = DurationSerializer::class) var duration: Duration = Duration.ZERO,
    @SerialName("StartTime") @Serializable(with = LocalDateTimeSerializer::class) var startTime: LocalDateTime = LocalDateTime.MIN,
    @SerialName("EndTime") @Serializable(with = LocalDateTimeSerializer::class) var endTime: LocalDateTime? = null,
    @SerialName("IsCompleted") var isCompleted: Boolean = false,
    @SerialName("Notes") var notes: String? = null,
)

@Serializable
data class AppSetting(
    @SerialName("Key") var key: String = "",
    @SerialName("Value") var value: String = "",
)

@Serializable
data class AiProvider(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    @SerialName("EncryptedApiKey") var encryptedApiKey: String? = null,
    @SerialName("BaseUrl") var baseUrl: String = "https://api.deepseek.com",
    @SerialName("Model") var model: String = "deepseek-chat",
    /** 0=OpenAI 兼容 */
    @SerialName("ApiFormat") var apiFormat: Int = 0,
    @SerialName("IsDefault") var isDefault: Boolean = false,
    @SerialName("IsBuiltIn") var isBuiltIn: Boolean = false,
)

// ============ 自定义模块（可扩展记录块，两端通用格式） ============

@Serializable
data class CustomModuleField(
    @SerialName("Key") var key: String = "",
    @SerialName("Label") var label: String = "",
    /** number=数值 text=文本 time=时间(bool 无 unit) bool=是否 select=单选 */
    @SerialName("Type") var type: String = "number",
    @SerialName("Unit") var unit: String? = null,
    /** select 类型的候选值，逗号分隔 */
    @SerialName("Options") var options: String? = null,
)

@Serializable
data class CustomModuleRecord(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Date") var date: String = "",
    @SerialName("Time") var time: String = "",
    @SerialName("Values") var values: Map<String, String> = emptyMap(),
    @SerialName("Note") var note: String? = null,
)

@Serializable
data class CustomModule(
    @SerialName("Id") var id: Int = 0,
    @SerialName("Name") var name: String = "",
    /** 图标索引（两端内置同一组 Material 图标，见 ModuleIcons） */
    @SerialName("Icon") var icon: Int = 0,
    @SerialName("ColorHex") var colorHex: String = "#4F6EF7",
    @SerialName("Fields") var fields: List<CustomModuleField> = emptyList(),
    @SerialName("Records") var records: MutableList<CustomModuleRecord> = mutableListOf(),
    @SerialName("CreatedAt") @Serializable(with = LocalDateTimeSerializer::class) var createdAt: LocalDateTime = LocalDateTime.MIN,
    @SerialName("IsDeleted") var isDeleted: Boolean = false,
)
