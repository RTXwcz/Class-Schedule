package com.kebiao.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.data.JsonScheduleCodec
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleOverrideRecord
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.ScheduleRules
import com.kebiao.app.data.toScheduleCourse
import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.ScheduleResolver
import com.kebiao.app.notifications.PeriodSchedule
import com.kebiao.app.notifications.LessonPeriod
import kotlinx.serialization.json.Json
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.imports.OpenAiImageImporter
import com.kebiao.app.imports.ImportValidation
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.CourseTableParser
import com.kebiao.app.ocr.OcrModelManager
import com.kebiao.app.ocr.OcrTextBlock
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class AppUiState(
    val courses: List<Course> = emptyList(),
    val exams: List<ScheduleExam> = emptyList(),
    val overrides: List<ScheduleOverride> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selectedDate: LocalDate = LocalDate.now(),
    val errorMessage: String? = null,
    val importDrafts: List<CourseDraft>? = null,
    val importPeriods: List<com.kebiao.app.notifications.LessonPeriod>? = null,
    val removedImportDraft: Pair<Int, CourseDraft>? = null,
    val importBusy: Boolean = false,
    val importStatus: String? = null,
    val importImageUri: android.net.Uri? = null,
    val importSource: String = "OPENAI",
    val settingsLoaded: Boolean = false,
    val modelInstalled: Boolean = false,
    val modelDownloadBusy: Boolean = false,
    val modelProgress: Float = 0f,
    val modelStatus: String? = null,
    val metadata: ScheduleExport = ScheduleExport(),
)

/** Coordinates UI state and persistence. Screens never access Room directly. */
class AppViewModel(
    private val repository: ScheduleRepository? = null,
    private val settingsStore: AppSettingsStore? = null,
    private val resolver: ScheduleResolver = ScheduleResolver(),
    private val openAiImporter: OpenAiImageImporter? = null,
    private val localOcrManager: OcrModelManager? = null,
    private val recognizeLocal: (suspend (android.net.Uri, OcrModelManager.ModelId) -> List<OcrTextBlock>)? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var modelJob: Job? = null
    private val settingsMutex = Mutex()

    init {
        if (repository != null && settingsStore != null) viewModelScope.launch {
            try {
                repository.ensureRules(settingsStore.settings.first())
                combine(repository.observeSnapshot(), settingsStore.settings) { data, preferences ->
                    data to ScheduleRules.read(data).apply(preferences)
                }.collect { (data, settings) ->
                    updateState { copy(courses = data.courses.mapNotNull(::toDomainCourse), exams = data.exams,
                        overrides = data.overrides.mapNotNull(::toDomainOverride),
                        metadata = data.copy(courses = emptyList(), exams = emptyList(), overrides = emptyList()),
                        modelStatus = if (this.settings.localOcrModel != settings.localOcrModel && !modelDownloadBusy) null else modelStatus,
                        modelProgress = if (this.settings.localOcrModel != settings.localOcrModel && !modelDownloadBusy) 0f else modelProgress,
                        settings = settings, settingsLoaded = true,
                        modelInstalled = localOcrManager?.isInstalled(modelId(settings.localOcrModel)) == true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { updateState { copy(errorMessage = "课表读取失败：${error.message}") } }
        }
    }

    fun selectDate(date: LocalDate) = updateState { copy(selectedDate = date) }

    fun addCourse(course: Course) {
        if (course.endPeriod > uiState.value.settings.periods.size) {
            updateState { copy(errorMessage = "课程超出当前每天节数，请先在设置中调整作息") }
            return
        }
        mutate({ copy(courses = courses.filterNot { it.id == course.id } + course, errorMessage = null) }) {
            upsertCourse(course.toScheduleCourse())
        }
    }

    fun updateCourse(course: Course) = addCourse(course)

    fun deleteCourse(id: String) {
        mutate({ copy(courses = courses.filterNot { it.id == id }, errorMessage = null) }) { deleteCourse(id) }
    }

    fun addExam(exam: ScheduleExam) {
        mutate({ copy(exams = exams.filterNot { it.id == exam.id } + exam, errorMessage = null) }) { upsertExam(exam) }
    }

    fun updateExam(exam: ScheduleExam) = addExam(exam)

    fun deleteExam(id: String) {
        mutate({ copy(exams = exams.filterNot { it.id == id }, errorMessage = null) }) { deleteExam(id) }
    }

    fun addOverride(override: ScheduleOverride) {
        mutate({
            copy(overrides = overrides.filterNot { it.date == override.date } + override, errorMessage = null)
        }) {
            upsertOverride(ScheduleOverrideRecord(override.date.toString(), override.replacementWeekday, override.note))
        }
    }

    fun deleteOverride(date: LocalDate) {
        mutate({ copy(overrides = overrides.filterNot { it.date == date }, errorMessage = null) }) { deleteOverride(date.toString()) }
    }

    fun saveOverride(previous: LocalDate?, value: ScheduleOverride) {
        mutate({ copy(overrides = overrides.filterNot { it.date == previous || it.date == value.date } + value, errorMessage = null) }) {
            transaction {
                if (previous != null && previous != value.date) deleteOverride(previous.toString())
                upsertOverride(ScheduleOverrideRecord(value.date.toString(), value.replacementWeekday, value.note))
            }
        }
    }

    fun updateSemesterStartDate(date: LocalDate?) {
        updateSettings { it.copy(semesterStartDate = date?.toString()) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = updateSettingsAndThen(transform) { }

    fun updateSettingsAndThen(transform: (AppSettings) -> AppSettings, onSaved: () -> Unit) {
        val store = settingsStore
        if (store == null) {
            updateState { copy(settings = transform(settings), errorMessage = null) }
            onSaved()
            return
        }
        viewModelScope.launch {
            try {
                settingsMutex.withLock {
                    val preferences = store.settings.first()
                    val current = repository?.snapshotWithSettings(preferences)?.let { ScheduleRules.read(it).apply(preferences) } ?: preferences
                    val next = transform(current)
                    if (repository != null) {
                        if (ScheduleRules.from(next) != ScheduleRules.from(current)) repository.updateRules(ScheduleRules.from(next))
                        val nextPreferences = next.copy(periods = preferences.periods, semesterStartDate = preferences.semesterStartDate, parityEnabled = preferences.parityEnabled)
                        if (nextPreferences != preferences) store.update { nextPreferences }
                    } else store.update { next }
                }
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "设置保存失败") }
            }
        }
    }

    fun updatePeriodSchedule(periods: List<LessonPeriod>) {
        try {
            val checked = PeriodSchedule.validate(periods)
            require(uiState.value.courses.all { it.endPeriod <= checked.size }) { "已有课程超出新节数，请先调整课程" }
            updateSettings { it.copy(periods = checked) }
        } catch (error: IllegalArgumentException) {
            updateState { copy(errorMessage = error.message) }
        }
    }

    fun effectiveCourses(date: LocalDate): List<EffectiveCourse> = resolver.resolve(
        date = date,
        semesterStart = uiState.value.settings.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        courses = uiState.value.courses,
        overrides = uiState.value.overrides,
        parityEnabled = uiState.value.settings.parityEnabled,
    )

    fun importJson(json: String) {
        if (uiState.value.importBusy) return
        val export = runCatching { JsonScheduleCodec.decode(json) }.getOrElse { error ->
            updateState { copy(errorMessage = error.message ?: "导入失败", importStatus = null) }
            return
        }
        val importedSemester = (export.extraFields["semesterStartDate"] as? JsonPrimitive)?.contentOrNull
        val importedPeriods = runCatching {
            val periods = export.extraFields["periods"]?.let { PeriodSchedule.decode(it.toString()) } ?: uiState.value.settings.periods
            require(export.courses.all { it.endPeriod <= periods.size }) { "导入课程超出当前作息节数，请先调整每日作息，或在 JSON 中附带 periods" }
            periods
        }.getOrElse { error ->
            updateState { copy(errorMessage = error.message ?: "作息时间无效") }
            return
        }
        if (importedSemester != null && runCatching { LocalDate.parse(importedSemester) }.isFailure) {
            updateState { copy(errorMessage = "学期开始日期无效") }
            return
        }
        val prepared = runCatching { ScheduleRules.read(export, ScheduleRules.from(uiState.value.settings)).apply(export) }
            .getOrElse { error -> updateState { copy(errorMessage = error.message ?: "课表规则无效") }; return }
        if (repository == null) {
            updateState {
                copy(
                    courses = export.courses.mapNotNull(::toDomainCourse),
                    exams = export.exams,
                    overrides = export.overrides.mapNotNull(::toDomainOverride),
                    errorMessage = null,
                    importStatus = "导入成功",
                    metadata = prepared.copy(courses = emptyList(), exams = emptyList(), overrides = emptyList()),
                    settings = settings.copy(
                        semesterStartDate = if ("semesterStartDate" in export.extraFields) importedSemester else settings.semesterStartDate,
                        parityEnabled = (export.extraFields["parityEnabled"] as? JsonPrimitive)?.booleanOrNull ?: settings.parityEnabled,
                        periods = importedPeriods,
                    ),
                )
            }
            return
        }
        updateState { copy(importBusy = true, importStatus = "正在导入", errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.replaceAll(prepared)
                updateState { copy(importStatus = "导入成功") }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "导入失败", importStatus = null) }
            } finally { updateState { copy(importBusy = false) } }
        }
    }

    fun exportJson(): String = JsonScheduleCodec.encode(snapshot())
    suspend fun exportCurrentJson(): String {
        val data = repository?.snapshotWithSettings(settingsStore?.settings?.first() ?: uiState.value.settings) ?: snapshot()
        return JsonScheduleCodec.encode(data)
    }

    fun clearError() = updateState { copy(errorMessage = null) }

    fun saveOpenAiKey(key: String) { if (key.isNotBlank()) openAiImporter?.saveApiKey(key.trim()) }
    fun hasOpenAiKey(): Boolean = openAiImporter?.hasApiKey() == true
    suspend fun testOpenAiConnection(endpoint: String, model: String): String =
        requireNotNull(openAiImporter) { "图片导入尚未初始化" }.testConnection(endpoint, model)

    fun recognizeImage(uri: android.net.Uri, local: Boolean = false) {
        if (uiState.value.importBusy || uiState.value.importDrafts != null) return
        val settings = uiState.value.settings
        updateState { copy(importBusy = true, importDrafts = null, removedImportDraft = null, importImageUri = uri, importSource = if (local) "OCR" else "OPENAI",
            importStatus = "正在识别", errorMessage = null, importPeriods = null) }
        viewModelScope.launch {
            try {
                var detectedPeriods: List<com.kebiao.app.notifications.LessonPeriod>? = null
                val drafts = if (local) {
                    require(settings.useLocalOcr) { "请先选择本地 OCR 模型" }
                    val blocks = requireNotNull(recognizeLocal)(uri, modelId(settings.localOcrModel))
                    val parser = CourseTableParser()
                    // The image's own time axis tells how many periods a day has and when each runs.
                    detectedPeriods = parser.parsePeriods(blocks).takeIf { it.size >= 4 }
                    parser.parse(blocks)
                } else requireNotNull(openAiImporter) { "图片导入尚未初始化" }
                    .importUri(uri, settings.openAiEndpoint, settings.openAiModel)
                require(drafts.isNotEmpty()) { "未识别到课程，请选择更清晰的图片" }
                val reviewedDefaults = if (settings.parityEnabled) drafts else drafts.map { draft ->
                    draft.copy(weekRule = draft.weekRule.copy(
                        value = draft.weekRule.value ?: com.kebiao.app.domain.model.WeekRule.ALL, confirmed = true))
                }
                updateState { copy(importDrafts = reviewedDefaults, importPeriods = detectedPeriods, importStatus = null) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "图片识别失败", importStatus = null) }
            } finally { updateState { copy(importBusy = false) } }
        }
    }

    /** Writes the schedule read from the image into 设置 → 每日作息. */
    fun applyImportedPeriods() {
        val periods = uiState.value.importPeriods ?: return
        if (periods.size < 2) return
        updateSettingsAndThen({ it.copy(periods = periods) }) { }
    }

    fun downloadLocalModel(id: OcrModelManager.ModelId = modelId(uiState.value.settings.localOcrModel)) {
        if (uiState.value.modelDownloadBusy || uiState.value.importBusy) return
        val manager = localOcrManager ?: return
        updateSettings { it.copy(useLocalOcr = true, ocrChoiceMade = true, localOcrModel = id.wireName) }
        updateState { copy(modelDownloadBusy = true, modelProgress = 0f, modelStatus = "正在下载 ${id.displayName}") }
        modelJob = viewModelScope.launch {
            try {
                manager.download(id) { progress ->
                    updateState { copy(modelProgress = progress.downloadedBytes.toFloat() / progress.totalBytes) }
                }
                updateState { copy(modelInstalled = true, modelStatus = "模型已就绪，可离线识别") }
            } catch (cancelled: CancellationException) {
                updateState { copy(modelStatus = "下载已取消") }
                throw cancelled
            } catch (error: Exception) {
                updateState { copy(modelStatus = "模型下载失败：${error.message}") }
            } finally { updateState { copy(modelDownloadBusy = false) } }
        }
    }

    fun cancelModelDownload() { modelJob?.cancel() }

    fun deleteLocalModel() {
        if (uiState.value.modelDownloadBusy || uiState.value.importBusy) return
        val manager = localOcrManager ?: return
        val id = modelId(uiState.value.settings.localOcrModel)
        viewModelScope.launch {
            try {
                manager.delete(id)
                updateState { copy(modelInstalled = false, modelStatus = "本地模型已删除") }
            } catch (error: Exception) { updateState { copy(modelStatus = "删除失败：${error.message}") } }
        }
    }

    fun editImportDrafts(drafts: List<CourseDraft>) {
        if (!uiState.value.importBusy) updateState { copy(importDrafts = drafts, removedImportDraft = if (importDrafts == null) null else removedImportDraft) }
    }

    fun removeImportDraft(id: String) {
        if (uiState.value.importBusy) return
        updateState {
            val drafts = importDrafts ?: return@updateState this
            val index = drafts.indexOfFirst { it.reviewId == id }
            if (index < 0) this else copy(importDrafts = drafts.filterNot { it.reviewId == id }, removedImportDraft = index to drafts[index])
        }
    }

    fun undoImportRemoval() {
        if (uiState.value.importBusy) return
        updateState {
            val drafts = importDrafts ?: return@updateState this
            val (index, draft) = removedImportDraft ?: return@updateState this
            copy(importDrafts = if (drafts.any { it.reviewId == draft.reviewId }) drafts else drafts.toMutableList().apply { add(index.coerceIn(0, size), draft) }, removedImportDraft = null)
        }
    }

    fun cancelImport() {
        if (!uiState.value.importBusy) updateState { copy(importDrafts = null, removedImportDraft = null, importImageUri = null, importStatus = null) }
    }

    fun saveImportDrafts(drafts: List<CourseDraft>) {
        val state = uiState.value
        // A queued tap must not apply an abandoned, edited, or already applied review again.
        if (state.importBusy || state.importDrafts != drafts || drafts.isEmpty()) return
        val invalid = drafts.withIndex().firstNotNullOfOrNull { (index, draft) ->
            ImportValidation.validate(draft, state.settings.periods.size).firstOrNull()?.let { index to it }
        }
        if (invalid != null) {
            updateState { copy(errorMessage = "第 ${invalid.first + 1} 门课程：${invalid.second.message}") }
            return
        }
        val source = state.importSource
        updateState { copy(importBusy = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val courses = drafts.map { draft ->
                    ScheduleCourse(
                        id = newCourseId(), name = draft.name.value.trim(), weekday = requireNotNull(draft.weekday.value),
                        startPeriod = requireNotNull(draft.startPeriod.value), endPeriod = requireNotNull(draft.endPeriod.value),
                        weekRule = requireNotNull(draft.weekRule.value).name, building = draft.building.value?.trim()?.ifBlank { null },
                        room = draft.room.value?.trim()?.ifBlank { null }, locationNote = draft.locationNote.value?.trim()?.ifBlank { null },
                        source = source, createdAtEpochMillis = now, updatedAtEpochMillis = now,
                        teacher = draft.teacher.value?.trim()?.ifBlank { null },
                        weeks = com.kebiao.app.domain.WeekSelection.parse(draft.weeks.value.orEmpty()),
                        courseNote = draft.courseNote.value?.trim()?.ifBlank { null },
                    )
                }
                requireNotNull(repository) { "数据库尚未初始化" }.appendCourses(courses, source = source)
                updateState { copy(importDrafts = null, removedImportDraft = null, importImageUri = null, importStatus = "已追加 ${courses.size} 门课程") }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "保存失败") }
            } finally { updateState { copy(importBusy = false) } }
        }
    }

    private fun mutate(inMemory: AppUiState.() -> AppUiState, operation: suspend ScheduleRepository.() -> Unit) {
        val store = repository
        if (store == null) {
            updateState(inMemory)
            return
        }
        viewModelScope.launch {
            try {
                store.operation()
                clearError()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "保存失败") }
            }
        }
    }

    private fun snapshot(): ScheduleExport = uiState.value.metadata.copy(
        extraFields = uiState.value.metadata.extraFields + mapOf(
            "parityEnabled" to JsonPrimitive(uiState.value.settings.parityEnabled),
            "periods" to Json.parseToJsonElement(PeriodSchedule.encode(uiState.value.settings.periods)),
            "semesterStartDate" to (uiState.value.settings.semesterStartDate?.let(::JsonPrimitive) ?: JsonNull),
        ),
        courses = uiState.value.courses.map { course ->
            ScheduleCourse(
                id = course.id,
                name = course.name,
                weekday = course.weekday,
                startPeriod = course.startPeriod,
                endPeriod = course.endPeriod,
                weekRule = course.weekRule.name,
                building = course.building,
                room = course.room,
                locationNote = course.locationNote,
                source = course.source,
                createdAtEpochMillis = course.createdAtEpochMillis,
                updatedAtEpochMillis = course.updatedAtEpochMillis,
                teacher = course.teacher, weeks = course.weeks, courseNote = course.courseNote,
            )
        },
        exams = uiState.value.exams,
        overrides = uiState.value.overrides.map { override ->
            ScheduleOverrideRecord(override.date.toString(), override.replacementWeekday, override.note)
        },
    )

    private fun updateState(transform: AppUiState.() -> AppUiState) {
        _uiState.update(transform)
    }

    companion object {
        fun modelId(value: String) = OcrModelManager.ModelId.entries.firstOrNull { it.wireName == value } ?: OcrModelManager.ModelId.TINY
        fun newCourseId(): String = UUID.randomUUID().toString()
        fun newExamId(): String = UUID.randomUUID().toString()

        private fun toDomainCourse(course: ScheduleCourse): Course? = runCatching {
            Course(
                id = course.id,
                name = course.name,
                weekday = course.weekday,
                startPeriod = course.startPeriod,
                endPeriod = course.endPeriod,
                weekRule = runCatching { com.kebiao.app.domain.model.WeekRule.valueOf(course.weekRule.uppercase()) }
                    .getOrDefault(com.kebiao.app.domain.model.WeekRule.ALL),
                building = course.building,
                room = course.room,
                locationNote = course.locationNote,
                source = course.source,
                createdAtEpochMillis = course.createdAtEpochMillis,
                updatedAtEpochMillis = course.updatedAtEpochMillis,
                teacher = course.teacher, weeks = course.weeks, courseNote = course.courseNote,
            )
        }.getOrNull()

        private fun toDomainOverride(record: ScheduleOverrideRecord): ScheduleOverride? = runCatching {
            ScheduleOverride(LocalDate.parse(record.date), record.replacementWeekday, record.note)
        }.getOrNull()
    }
}
