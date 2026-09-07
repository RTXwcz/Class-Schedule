package com.kebiao.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.data.JsonScheduleCodec
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleOverrideRecord
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.toScheduleCourse
import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.ScheduleResolver
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.imports.OpenAiImageImporter
import com.kebiao.app.imports.ImportValidation
import com.kebiao.app.ocr.CourseDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val importBusy: Boolean = false,
    val importStatus: String? = null,
    val importImageUri: android.net.Uri? = null,
)

/** Coordinates UI state and persistence. Screens never access Room directly. */
class AppViewModel(
    private val repository: ScheduleRepository? = null,
    private val settingsStore: AppSettingsStore? = null,
    private val resolver: ScheduleResolver = ScheduleResolver(),
    private val openAiImporter: OpenAiImageImporter? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        repository?.observeCourses()?.onEach { courses -> updateState { copy(courses = courses) } }?.launchIn(viewModelScope)
        repository?.observeExams()?.onEach { exams -> updateState { copy(exams = exams) } }?.launchIn(viewModelScope)
        repository?.observeOverrides()?.onEach { records ->
            updateState { copy(overrides = records.mapNotNull(::toDomainOverride)) }
        }?.launchIn(viewModelScope)
        settingsStore?.settings?.onEach { settings -> updateState { copy(settings = settings) } }?.launchIn(viewModelScope)
    }

    fun selectDate(date: LocalDate) = updateState { copy(selectedDate = date) }

    fun addCourse(course: Course) {
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

    fun updateSemesterStartDate(date: LocalDate?) {
        updateState { copy(settings = settings.copy(semesterStartDate = date?.toString()), errorMessage = null) }
        settingsStore?.let { store ->
            viewModelScope.launch { store.update { it.copy(semesterStartDate = date?.toString()) } }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(uiState.value.settings)
        updateState { copy(settings = next, errorMessage = null) }
        settingsStore?.let { store -> viewModelScope.launch { store.update(transform) } }
    }

    fun effectiveCourses(date: LocalDate): List<EffectiveCourse> = resolver.resolve(
        date = date,
        semesterStart = uiState.value.settings.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        courses = uiState.value.courses,
        overrides = uiState.value.overrides,
    )

    fun importJson(json: String) {
        if (uiState.value.importBusy) return
        val export = runCatching { JsonScheduleCodec.decode(json) }.getOrElse { error ->
            updateState { copy(errorMessage = error.message ?: "导入失败", importStatus = null) }
            return
        }
        if (repository == null) {
            updateState {
                copy(
                    courses = export.courses.mapNotNull(::toDomainCourse),
                    exams = export.exams,
                    overrides = export.overrides.mapNotNull(::toDomainOverride),
                    errorMessage = null,
                    importStatus = "导入成功",
                )
            }
            return
        }
        updateState { copy(importBusy = true, importStatus = "正在导入", errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.replaceAll(export)
                updateState { copy(importStatus = "导入成功") }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "导入失败", importStatus = null) }
            } finally { updateState { copy(importBusy = false) } }
        }
    }

    fun exportJson(): String = JsonScheduleCodec.encode(snapshot())

    fun clearError() = updateState { copy(errorMessage = null) }

    fun saveOpenAiKey(key: String) { if (key.isNotBlank()) openAiImporter?.saveApiKey(key.trim()) }
    fun hasOpenAiKey(): Boolean = openAiImporter?.hasApiKey() == true

    fun recognizeImage(uri: android.net.Uri) {
        if (uiState.value.importBusy) return
        val settings = uiState.value.settings
        updateState { copy(importBusy = true, importDrafts = null, importImageUri = uri, importStatus = "正在识别", errorMessage = null) }
        viewModelScope.launch {
            try {
                val drafts = requireNotNull(openAiImporter) { "图片导入尚未初始化" }
                    .importUri(uri, settings.openAiEndpoint, settings.openAiModel)
                updateState { copy(importDrafts = drafts, importStatus = null) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                updateState { copy(errorMessage = error.message ?: "图片识别失败", importStatus = null) }
            } finally { updateState { copy(importBusy = false) } }
        }
    }

    fun editImportDrafts(drafts: List<CourseDraft>) {
        if (!uiState.value.importBusy) updateState { copy(importDrafts = drafts) }
    }

    fun cancelImport() {
        if (!uiState.value.importBusy) updateState { copy(importDrafts = null, importImageUri = null, importStatus = null) }
    }

    fun saveImportDrafts(drafts: List<CourseDraft>) {
        if (uiState.value.importBusy || !ImportValidation.canPersist(drafts)) return
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
                        source = "OPENAI", createdAtEpochMillis = now, updatedAtEpochMillis = now,
                    )
                }
                requireNotNull(repository) { "数据库尚未初始化" }.appendCourses(courses)
                updateState { copy(importDrafts = null, importImageUri = null, importStatus = "已追加 ${courses.size} 门课程") }
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

    private fun snapshot(): ScheduleExport = ScheduleExport(
        schemaVersion = 1,
        datasetId = "default",
        updatedAt = java.time.Instant.now().toString(),
        source = "NATIVE",
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
            )
        },
        exams = uiState.value.exams,
        overrides = uiState.value.overrides.map { override ->
            ScheduleOverrideRecord(override.date.toString(), override.replacementWeekday, override.note)
        },
    )

    private fun updateState(transform: AppUiState.() -> AppUiState) {
        _uiState.value = transform(_uiState.value)
    }

    companion object {
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
            )
        }.getOrNull()

        private fun toDomainOverride(record: ScheduleOverrideRecord): ScheduleOverride? = runCatching {
            ScheduleOverride(LocalDate.parse(record.date), record.replacementWeekday, record.note)
        }.getOrNull()
    }
}
