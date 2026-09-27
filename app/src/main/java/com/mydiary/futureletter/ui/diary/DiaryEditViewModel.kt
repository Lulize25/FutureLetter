package com.mydiary.futureletter.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.data.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DiaryEditUiState(
    val entryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val content: String = "",
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    /** 是否有未保存的修改 */
    val dirty: Boolean = false,
    /** 用户选了已有日记的日期：待确认 */
    val pendingDateChange: LocalDate? = null,
    val conflictEntryTitle: String? = null
)

@HiltViewModel
class DiaryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val navEntryId: Long? = savedStateHandle.get<String>("entryId")?.toLongOrNull()
    private val navDateMillis: Long? = savedStateHandle.get<String>("dateMillis")?.toLongOrNull()

    private val _uiState = MutableStateFlow(DiaryEditUiState())
    val uiState: StateFlow<DiaryEditUiState> = _uiState

    // 加载时的快照，用于判断是否有修改
    private var snapshotTitle: String = ""
    private var snapshotContent: String = ""
    private var snapshotDate: LocalDate? = null

    init {
        viewModelScope.launch {
            val entry: DiaryEntry? = when {
                navEntryId != null -> diaryRepository.getEntry(navEntryId)
                navDateMillis != null -> diaryRepository.getEntryByDate(navDateMillis)
                else -> null
            }
            val date = navDateMillis?.let {
                java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: LocalDate.now()

            val state = DiaryEditUiState(
                entryId = entry?.id,
                date = date,
                title = entry?.title ?: "",
                content = entry?.contentMd ?: "",
                loaded = true,
                isNew = entry == null
            )
            takeSnapshot(state)
            _uiState.value = state
        }
    }

    private fun takeSnapshot(state: DiaryEditUiState) {
        snapshotTitle = state.title
        snapshotContent = state.content
        snapshotDate = state.date
    }

    private fun computeDirty(s: DiaryEditUiState): Boolean =
        s.loaded && (s.title != snapshotTitle ||
            s.content != snapshotContent ||
            s.date != snapshotDate)

    private fun update(transform: DiaryEditUiState.() -> DiaryEditUiState) {
        val s = _uiState.value.transform()
        _uiState.value = s.copy(dirty = computeDirty(s))
    }

    fun updateTitle(title: String) = update { copy(title = title) }

    fun updateContent(content: String) = update { copy(content = content) }

    /** 选择日期：若目标日期已有别的日记，先弹确认 */
    fun requestDateChange(date: LocalDate) {
        viewModelScope.launch {
            val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val existing = diaryRepository.getEntryByDate(millis)
            if (existing != null && existing.id != _uiState.value.entryId) {
                _uiState.value = _uiState.value.copy(
                    pendingDateChange = date,
                    conflictEntryTitle = existing.title.ifBlank { "（无标题）" }
                )
            } else {
                update { copy(date = date) }
            }
        }
    }

    /** 确认覆盖目标日期的已有日记 */
    fun confirmDateChange() {
        val target = _uiState.value.pendingDateChange ?: return
        viewModelScope.launch {
            val millis = target.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            diaryRepository.getEntryByDate(millis)?.let {
                if (it.id != _uiState.value.entryId) diaryRepository.deleteEntry(it.id)
            }
            update {
                copy(date = target, pendingDateChange = null, conflictEntryTitle = null)
            }
        }
    }

    fun cancelDateChange() {
        _uiState.value = _uiState.value.copy(
            pendingDateChange = null,
            conflictEntryTitle = null
        )
    }

    /** 显式保存（确定键） */
    fun saveNow(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        if (!state.loaded) {
            onSaved()
            return
        }
        viewModelScope.launch {
            saveInternal(state)
            takeSnapshot(_uiState.value)
            _uiState.value = _uiState.value.copy(dirty = computeDirty(_uiState.value))
            onSaved()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _uiState.value.entryId ?: return
        viewModelScope.launch {
            diaryRepository.deleteEntry(id)
            onDone()
        }
    }

    private suspend fun saveInternal(state: DiaryEditUiState) {
        // 标题与正文都为空则不落库（避免空日记）
        if (state.title.isBlank() && state.content.isBlank()) return

        val now = System.currentTimeMillis()
        val dateMillis = state.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 标题留空时取正文第一行
        val title = state.title.ifBlank {
            state.content.lineSequence().firstOrNull { it.isNotBlank() }
                ?.take(30)?.trim() ?: ""
        }

        val existingId = state.entryId
            ?: diaryRepository.getEntryByDate(dateMillis)?.id

        val entry = DiaryEntry(
            id = existingId ?: 0,
            title = title,
            contentMd = state.content,
            date = dateMillis,
            createdAt = now,
            updatedAt = now,
            isDraft = false
        )
        val newId = diaryRepository.saveEntry(entry)
        if (_uiState.value.entryId == null) {
            _uiState.value = _uiState.value.copy(entryId = newId, isNew = false)
        }
    }
}
