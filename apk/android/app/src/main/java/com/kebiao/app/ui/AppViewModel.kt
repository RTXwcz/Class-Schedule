package com.kebiao.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.data.JsonScheduleCodec
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleOverrideRecord
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.ScheduleResolver
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.ScheduleOverride
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
)

/** Coordinates UI state and persistence. Screens never access Room directly. */
class AppViewModel(
    private val repository: ScheduleRepository? = null,
    private val settingsStore: AppSettingsStore? = null,
    private val resolver: ScheduleResolver = ScheduleResolver(),
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
        updateState { copy(courses = courses.filterNot { it.id == course.id } + course, errorMessage = null) }
        persist()
    }

    fun updateCourse(course: Course) = addCourse(course)

    fun deleteCourse(id: String) {
        updateState { copy(courses = courses.filterNot { it.id == id }, errorMessage = null) }
        persist()
    }

    fun addExam(exam: ScheduleExam) {
        updateState { copy(exams = exams.filterNot { it.id == exam.id } + exam, errorMessage = null) }
        persist()
    }

    fun updateExam(exam: ScheduleExam) = addExam(exam)

    fun deleteExam(id: String) {
        updateState { copy(exams = exams.filterNot { it.id == id }, errorMessage = null) }
        persist()
    }

    fun addOverride(override: ScheduleOverride) {
        updateState {
            copy(overrides = overrides.filterNot { it.date == override.date } + override, errorMessage = null)
        }
        persist()
    }

    fun deleteOverride(date: LocalDate) {
        updateState { copy(overrides = overrides.filterNot { it.date == date }, errorMessage = null) }
        persist()
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

    fun importJson(json: String): Boolean {
        return runCatching {
            val export = JsonScheduleCodec.decode(json)
            updateState {
                copy(
                    courses = export.courses.mapNotNull(::toDomainCourse),
                    exams = export.exams,
                    overrides = export.overrides.mapNotNull(::toDomainOverride),
                    errorMessage = null,
                )
            }
            persist()
        }.onFailure { error -> updateState { copy(errorMessage = error.message ?: "导入失败") } }.isSuccess
    }

    fun exportJson(): String = JsonScheduleCodec.encode(snapshot())

    fun clearError() = updateState { copy(errorMessage = null) }

    private fun persist() {
        val repository = repository ?: return
        val snapshot = snapshot()
        viewModelScope.launch { repository.replaceAll(snapshot) }
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
