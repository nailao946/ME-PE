package com.joe.mepe.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** 与桌面端 TaskRepository/GoalRepository 等对应的数据仓库（单例） */
object Repos {

    // ---------- tasks ----------
    private val taskK = ListSerializer(TaskItem.serializer())
    fun tasks(includeDeleted: Boolean = false): MutableList<TaskItem> {
        val list = JsonStore.loadList("tasks") { f -> JsonStore.json.decodeFromString(taskK, f.readText()) }
        return if (includeDeleted) list else list.filter { !it.isDeleted }.toMutableList()
    }

    fun saveTasks(list: List<TaskItem>) {
        JsonStore.saveText("tasks", JsonStore.json.encodeToString(taskK, list))
        DataBus.bump()
    }

    fun addTask(t: TaskItem): Int {
        val all = JsonStore.loadList("tasks") { f -> JsonStore.json.decodeFromString(taskK, f.readText()) }
        t.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        t.createdAt = LocalDateTime.now()
        t.updatedAt = LocalDateTime.now()
        t.sortOrder = (all.filter { !it.isDeleted }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        all.add(t)
        saveTasks(all)
        return t.id
    }

    fun updateTask(t: TaskItem) {
        val all = JsonStore.loadList("tasks") { f -> JsonStore.json.decodeFromString(taskK, f.readText()) }
        val i = all.indexOfFirst { it.id == t.id }
        if (i >= 0) { t.updatedAt = LocalDateTime.now(); all[i] = t }
        saveTasks(all)
    }

    fun softDeleteTask(id: Int, deleted: Boolean = true) {
        val all = JsonStore.loadList("tasks") { f -> JsonStore.json.decodeFromString(taskK, f.readText()) }
        val t = all.firstOrNull { it.id == id } ?: return
        t.isDeleted = deleted
        t.deletedAt = if (deleted) LocalDateTime.now() else LocalDateTime.MIN
        // 子任务一起处理
        all.filter { it.parentTaskId == id }.forEach { it.isDeleted = deleted }
        saveTasks(all)
    }

    /** 手动排序：同级（同父任务）内上移/下移，交换 SortOrder（与桌面端兼容） */
    fun moveTask(id: Int, up: Boolean) {
        val list = tasks().filter { it.parentTaskId == tasks().firstOrNull { it.id == id }?.parentTaskId }
            .sortedWith(compareBy({ -it.priority }, { it.sortOrder })).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val target = if (up) idx - 1 else idx + 1
        if (target < 0 || target >= list.size) return
        val a = list[idx]; val b = list[target]
        val tmp = a.sortOrder; a.sortOrder = b.sortOrder; b.sortOrder = tmp
        val tmpP = a.priority; a.priority = b.priority; b.priority = tmpP
        updateTask(a); updateTask(b)
    }

    // ---------- task completions ----------
    private val tcK = ListSerializer(TaskCompletionRecord.serializer())
    fun completions(): MutableList<TaskCompletionRecord> =
        JsonStore.loadList("task_completions") { f -> JsonStore.json.decodeFromString(tcK, f.readText()) }

    fun saveCompletions(list: List<TaskCompletionRecord>) {
        JsonStore.saveText("task_completions", JsonStore.json.encodeToString(tcK, list))
        DataBus.bump()
    }

    fun addCompletion(taskId: Int, date: LocalDate) {
        val all = completions()
        all.add(TaskCompletionRecord(id = (all.maxOfOrNull { it.id } ?: 0) + 1, taskId = taskId,
            date = date.toString(), completedAt = LocalDateTime.now()))
        saveCompletions(all)
    }

    fun removeCompletion(taskId: Int, date: LocalDate) {
        saveCompletions(completions().filterNot { it.taskId == taskId && it.date == date.toString() })
    }

    // ---------- goals ----------
    private val goalK = ListSerializer(Goal.serializer())
    fun goals(includeDeleted: Boolean = false): MutableList<Goal> {
        val list = JsonStore.loadList("goals") { f -> JsonStore.json.decodeFromString(goalK, f.readText()) }
        return if (includeDeleted) list else list.filter { !it.isDeleted }.toMutableList()
    }

    fun saveGoals(list: List<Goal>) {
        JsonStore.saveText("goals", JsonStore.json.encodeToString(goalK, list))
        DataBus.bump()
    }

    fun addGoal(g: Goal): Int {
        val all = JsonStore.loadList("goals") { f -> JsonStore.json.decodeFromString(goalK, f.readText()) }
        g.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        g.createdAt = LocalDateTime.now(); g.updatedAt = LocalDateTime.now()
        g.sortOrder = (all.filter { !it.isDeleted }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        all.add(g)
        saveGoals(all)
        return g.id
    }

    fun updateGoal(g: Goal) {
        val all = JsonStore.loadList("goals") { f -> JsonStore.json.decodeFromString(goalK, f.readText()) }
        val i = all.indexOfFirst { it.id == g.id }
        if (i >= 0) { g.updatedAt = LocalDateTime.now(); all[i] = g }
        saveGoals(all)
    }

    fun deleteGoal(id: Int) {
        val all = JsonStore.loadList("goals") { f -> JsonStore.json.decodeFromString(goalK, f.readText()) }
        all.firstOrNull { it.id == id }?.isDeleted = true
        all.filter { it.parentId == id }.forEach { it.isDeleted = true }
        saveGoals(all)
    }

    // ---------- goal tags ----------
    private val tagK = ListSerializer(GoalTag.serializer())
    fun tags(): MutableList<GoalTag> =
        JsonStore.loadList("tags") { f -> JsonStore.json.decodeFromString(tagK, f.readText()) }.sortedBy { it.sortOrder }.toMutableList()

    fun saveTags(list: List<GoalTag>) {
        JsonStore.saveText("tags", JsonStore.json.encodeToString(tagK, list))
        DataBus.bump()
    }

    fun addTag(t: GoalTag): Int {
        val all = JsonStore.loadList("tags") { f -> JsonStore.json.decodeFromString(tagK, f.readText()) }
        t.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        t.sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
        t.createdAt = LocalDateTime.now()
        all.add(t)
        saveTags(all)
        return t.id
    }

    fun deleteTag(id: Int) = saveTags(tags().filterNot { it.id == id })

    fun updateTag(t: GoalTag) {
        val all = tags()
        val i = all.indexOfFirst { it.id == t.id }
        if (i >= 0) { all[i] = t; saveTags(all) }
    }

    // ---------- time tags & records ----------
    private val timeTagK = ListSerializer(TimeTag.serializer())
    fun timeTags(): MutableList<TimeTag> =
        JsonStore.loadList("time_tags") { f -> JsonStore.json.decodeFromString(timeTagK, f.readText()) }.sortedBy { it.sortOrder }.toMutableList()

    fun saveTimeTags(list: List<TimeTag>) {
        JsonStore.saveText("time_tags", JsonStore.json.encodeToString(timeTagK, list))
        DataBus.bump()
    }

    fun addTimeTag(t: TimeTag): Int {
        val all = JsonStore.loadList("time_tags") { f -> JsonStore.json.decodeFromString(timeTagK, f.readText()) }
        t.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        t.sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
        all.add(t)
        saveTimeTags(all)
        return t.id
    }

    private val timeRecK = ListSerializer(TimeRecord.serializer())
    fun timeRecords(): MutableList<TimeRecord> =
        JsonStore.loadList("time_records") { f -> JsonStore.json.decodeFromString(timeRecK, f.readText()) }

    fun saveTimeRecords(list: List<TimeRecord>) {
        JsonStore.saveText("time_records", JsonStore.json.encodeToString(timeRecK, list))
        DataBus.bump()
    }

    /** 开始计时：该标签若有未结束记录先自动结束 */
    fun startTimer(tagId: Int): TimeRecord? {
        val all = timeRecords()
        all.filter { it.endTime == null }.forEach { it.endTime = LocalDateTime.now() }
        val rec = TimeRecord(id = (all.maxOfOrNull { it.id } ?: 0) + 1, tagId = tagId,
            startTime = LocalDateTime.now(), date = LocalDate.now().toString())
        all.add(rec)
        saveTimeRecords(all)
        return rec
    }

    fun stopTimer(tagId: Int) {
        val all = timeRecords()
        all.lastOrNull { it.tagId == tagId && it.endTime == null }?.endTime = LocalDateTime.now()
        saveTimeRecords(all)
    }

    fun runningRecord(): TimeRecord? = timeRecords().lastOrNull { it.endTime == null }

    fun updateTimeTag(t: TimeTag) {
        val all = timeTags()
        val i = all.indexOfFirst { it.id == t.id }
        if (i >= 0) { all[i] = t; saveTimeTags(all) }
    }

    fun deleteTimeTag(id: Int) = saveTimeTags(timeTags().filterNot { it.id == id })

    fun deleteTimeRecord(id: Int) = saveTimeRecords(timeRecords().filterNot { it.id == id })

    fun stopAll() {
        val all = timeRecords()
        val open = all.any { it.endTime == null }
        if (open) { all.forEach { if (it.endTime == null) it.endTime = LocalDateTime.now() }; saveTimeRecords(all) }
    }

    // ---------- health ----------
    private val healthK = ListSerializer(HealthRecord.serializer())
    fun health(): MutableList<HealthRecord> =
        JsonStore.loadList("health_records") { f -> JsonStore.json.decodeFromString(healthK, f.readText()) }

    fun saveHealth(list: List<HealthRecord>) {
        JsonStore.saveText("health_records", JsonStore.json.encodeToString(healthK, list))
        DataBus.bump()
    }

    fun addHealth(type: String, date: LocalDate, value: Double, detail: String? = null, note: String? = null): HealthRecord {
        val all = health()
        val rec = HealthRecord(id = (all.maxOfOrNull { it.id } ?: 0) + 1, type = type, date = date.toString(),
            value = value, detail = detail, note = note, createdAt = LocalDateTime.now())
        all.add(rec)
        saveHealth(all)
        return rec
    }

    /** 按类型+日期(+detail 键)更新或插入（睡眠/体重/心情等一日一条） */
    fun upsertHealth(type: String, date: LocalDate, value: Double, detail: String? = null): HealthRecord {
        val all = health()
        val existing = all.lastOrNull { it.type == type && it.date == date.toString() }
        return if (existing != null) {
            existing.value = value; existing.detail = detail
            saveHealth(all); existing
        } else addHealth(type, date, value, detail)
    }

    // ---------- water containers ----------
    private val waterK = ListSerializer(WaterContainer.serializer())
    fun waterContainers(): MutableList<WaterContainer> =
        JsonStore.loadList("water_containers") { f -> JsonStore.json.decodeFromString(waterK, f.readText()) }

    fun saveWaterContainers(list: List<WaterContainer>) {
        JsonStore.saveText("water_containers", JsonStore.json.encodeToString(waterK, list))
        DataBus.bump()
    }

    fun addWaterContainer(w: WaterContainer): Int {
        val all = JsonStore.loadList("water_containers") { f -> JsonStore.json.decodeFromString(waterK, f.readText()) }
        w.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        all.add(w)
        saveWaterContainers(all)
        return w.id
    }

    fun deleteWaterContainer(id: Int) = saveWaterContainers(waterContainers().filterNot { it.id == id })

    // ---------- exercise ----------
    private val exK = ListSerializer(ExerciseItem.serializer())
    fun exercises(): MutableList<ExerciseItem> =
        JsonStore.loadList("exercise_items") { f -> JsonStore.json.decodeFromString(exK, f.readText()) }
            .filter { !it.isDeleted }.sortedWith(compareBy<ExerciseItem> { it.sortOrder }.thenBy { it.createdAt }).toMutableList()

    fun saveExercises(list: List<ExerciseItem>) {
        JsonStore.saveText("exercise_items", JsonStore.json.encodeToString(exK, list))
        DataBus.bump()
    }

    fun addExercise(e: ExerciseItem): Int {
        val all = JsonStore.loadList("exercise_items") { f -> JsonStore.json.decodeFromString(exK, f.readText()) }
        e.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        e.createdAt = LocalDateTime.now()
        e.sortOrder = (all.filter { !it.isDeleted }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        all.add(e)
        saveExercises(all)
        return e.id
    }

    fun updateExercise(e: ExerciseItem) {
        val all = JsonStore.loadList("exercise_items") { f -> JsonStore.json.decodeFromString(exK, f.readText()) }
        val i = all.indexOfFirst { it.id == e.id }
        if (i >= 0) all[i] = e
        saveExercises(all)
    }

    fun deleteExercise(id: Int) {
        val all = JsonStore.loadList("exercise_items") { f -> JsonStore.json.decodeFromString(exK, f.readText()) }
        all.firstOrNull { it.id == id }?.isDeleted = true
        saveExercises(all)
    }

    fun swapExerciseSort(idA: Int, idB: Int) {
        val all = JsonStore.loadList("exercise_items") { f -> JsonStore.json.decodeFromString(exK, f.readText()) }
        val a = all.firstOrNull { it.id == idA } ?: return
        val b = all.firstOrNull { it.id == idB } ?: return
        val t = a.sortOrder; a.sortOrder = b.sortOrder; b.sortOrder = t
        saveExercises(all)
    }

    /** 锻炼项目今日是否该做（每日/隔日/每周指定几天） */
    fun exerciseDueToday(e: ExerciseItem, date: LocalDate): Boolean {
        val created = e.createdAt.toLocalDate()
        if (date.isBefore(created)) return false
        return when (e.frequency) {
            "daily" -> true
            "everyOther" -> java.time.temporal.ChronoUnit.DAYS.between(created, date) % 2 == 0L
            "weekly" -> {
                val days = (e.weeklyDays ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }
                // 1=周一 … 7=周日（与桌面端一致）
                days.contains(date.dayOfWeek.value)
            }
            else -> true
        }
    }

    /** 某锻炼项目某日累计量 */
    fun exerciseSum(itemId: Int, date: LocalDate): Double =
        health().filter { it.type == HealthTypes.EXERCISE && it.date == date.toString() && it.detail == itemId.toString() }
            .sumOf { it.value }

    // ---------- medications ----------
    private val medK = ListSerializer(MedicationRecord.serializer())
    fun medications(): MutableList<MedicationRecord> =
        JsonStore.loadList("medications") { f -> JsonStore.json.decodeFromString(medK, f.readText()) }.filter { !it.isDeleted }.toMutableList()

    fun saveMedications(list: List<MedicationRecord>) {
        JsonStore.saveText("medications", JsonStore.json.encodeToString(medK, list))
        DataBus.bump()
    }

    fun addMedication(m: MedicationRecord): Int {
        val all = JsonStore.loadList("medications") { f -> JsonStore.json.decodeFromString(medK, f.readText()) }
        m.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        m.createdAt = LocalDateTime.now()
        all.add(m)
        saveMedications(all)
        return m.id
    }

    fun updateMedication(m: MedicationRecord) {
        val all = JsonStore.loadList("medications") { f -> JsonStore.json.decodeFromString(medK, f.readText()) }
        val i = all.indexOfFirst { it.id == m.id }
        if (i >= 0) all[i] = m
        saveMedications(all)
    }

    fun deleteMedication(id: Int) {
        val all = JsonStore.loadList("medications") { f -> JsonStore.json.decodeFromString(medK, f.readText()) }
        all.firstOrNull { it.id == id }?.isDeleted = true
        saveMedications(all)
    }

    /** 今日是否需要服用（按频率与起止日期） */
    fun medicationDueToday(m: MedicationRecord, date: LocalDate): Boolean {
        val start = m.startDate?.toLocalDate() ?: LocalDate.now()
        if (date.isBefore(start)) return false
        m.endDate?.let { if (date.isAfter(it.toLocalDate())) return false }
        return when (m.frequency) {
            1 -> java.time.temporal.ChronoUnit.DAYS.between(start, date) % m.frequencyN.coerceAtLeast(1) == 0L
            2 -> (m.weeklyDays ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.contains(date.dayOfWeek.value)
            4 -> false // 按需
            else -> true
        }
    }

    // ---------- reviews / visions / focus ----------
    private val reviewK = ListSerializer(Review.serializer())
    fun reviews(): MutableList<Review> =
        JsonStore.loadList("reviews") { f -> JsonStore.json.decodeFromString(reviewK, f.readText()) }

    fun addReview(r: Review): Int {
        val all = reviews()
        r.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        r.createdAt = LocalDateTime.now()
        all.add(r)
        JsonStore.saveText("reviews", JsonStore.json.encodeToString(reviewK, all))
        DataBus.bump()
        return r.id
    }

    fun saveReviews(list: List<Review>) {
        JsonStore.saveText("reviews", JsonStore.json.encodeToString(reviewK, list))
        DataBus.bump()
    }

    private val visionK = ListSerializer(Vision.serializer())
    fun visions(): MutableList<Vision> =
        JsonStore.loadList("visions") { f -> JsonStore.json.decodeFromString(visionK, f.readText()) }

    fun saveVision(v: Vision) {
        val all = visions()
        v.updatedAt = LocalDateTime.now()
        val i = all.indexOfFirst { it.id == v.id }
        if (i >= 0) all[i] = v else {
            v.id = (all.maxOfOrNull { it.id } ?: 0) + 1
            v.createdAt = LocalDateTime.now()
            all.add(v)
        }
        JsonStore.saveText("visions", JsonStore.json.encodeToString(visionK, all))
        DataBus.bump()
    }

    private val focusK = ListSerializer(FocusSession.serializer())
    fun focusSessions(): MutableList<FocusSession> =
        JsonStore.loadList("focus_sessions") { f -> JsonStore.json.decodeFromString(focusK, f.readText()) }

    fun addFocus(s: FocusSession): Int {
        val all = focusSessions()
        s.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        all.add(s)
        JsonStore.saveText("focus_sessions", JsonStore.json.encodeToString(focusK, all))
        DataBus.bump()
        return s.id
    }

    // ---------- custom modules（可扩展记录块） ----------
    private val moduleK = ListSerializer(CustomModule.serializer())
    fun customModules(): MutableList<CustomModule> =
        JsonStore.loadList("custom_modules") { f -> JsonStore.json.decodeFromString(moduleK, f.readText()) }

    fun saveCustomModules(list: List<CustomModule>) {
        JsonStore.saveText("custom_modules", JsonStore.json.encodeToString(moduleK, list))
        DataBus.bump()
    }

    fun addCustomModule(m: CustomModule): Int {
        val all = customModules()
        m.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        m.createdAt = LocalDateTime.now()
        all.add(m)
        saveCustomModules(all)
        return m.id
    }

    fun updateCustomModule(m: CustomModule) {
        val all = customModules()
        val i = all.indexOfFirst { it.id == m.id }
        if (i >= 0) { all[i] = m; saveCustomModules(all) }
    }

    fun deleteCustomModule(id: Int) =
        saveCustomModules(customModules().filterNot { it.id == id })

    /** 给模块加一条记录（重置该模块记录的 id 序列） */
    fun addModuleRecord(moduleId: Int, rec: CustomModuleRecord) {
        val all = customModules()
        val m = all.firstOrNull { it.id == moduleId } ?: return
        rec.id = (m.records.maxOfOrNull { it.id } ?: 0) + 1
        m.records.add(rec)
        saveCustomModules(all)
    }

    // ---------- settings ----------
    private val settingK = ListSerializer(AppSetting.serializer())
    fun settings(): MutableList<AppSetting> =
        JsonStore.loadList("settings") { f -> JsonStore.json.decodeFromString(settingK, f.readText()) }

    fun getSetting(key: String, default: String = ""): String =
        settings().firstOrNull { it.key == key }?.value ?: default

    fun setSetting(key: String, value: String) {
        val all = settings()
        val s = all.firstOrNull { it.key == key }
        if (s != null) s.value = value else all.add(AppSetting(key, value))
        JsonStore.saveText("settings", JsonStore.json.encodeToString(settingK, all))
        DataBus.bump()
    }

    // ---------- ai providers ----------
    private val aiK = ListSerializer(AiProvider.serializer())
    fun aiProviders(): MutableList<AiProvider> =
        JsonStore.loadList("ai_providers") { f -> JsonStore.json.decodeFromString(aiK, f.readText()) }

    fun saveAiProviders(list: List<AiProvider>) {
        val all = list
        // 保证唯一默认
        val def = all.firstOrNull { it.isDefault }
        all.forEach { it.isDefault = it.id == def?.id }
        JsonStore.saveText("ai_providers", JsonStore.json.encodeToString(aiK, all))
        DataBus.bump()
    }

    fun addAiProvider(p: AiProvider): Int {
        val all = aiProviders()
        p.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        all.add(p)
        saveAiProviders(all)
        return p.id
    }

    fun defaultAiProvider(): AiProvider? {
        val all = aiProviders()
        return all.firstOrNull { it.isDefault } ?: all.firstOrNull()
    }
}
