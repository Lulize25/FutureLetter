package com.mydiary.futureletter.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.data.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DiaryEditUiState(
    val entryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val loaded: Boolean = false,
    val isNew: Boolean = true
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DiaryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val navEntryId: Long? = savedStateHandle.get<String>("entryId")?.toLongOrNull()
    private val navDateMillis: Long? = savedStateHandle.get<String>("date")?.toLongOrNull()

    private val _uiState = MutableStateFlow(DiaryEditUiState())
    val uiState: StateFlow<DiaryEditUiState> = _uiState

    /** 自动保存开关：内容加载完成之后才开启 */
    @Volatile
    private var autoSaveEnabled = false

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

            val tags = entry?.let { diaryRepository.getTagsForDiary(it.id).map { t -> t.name } }
                ?: emptyList()

            _uiState.value = DiaryEditUiState(
                entryId = entry?.id,
                date = date,
                title = entry?.title ?: "",
                content = entry?.contentMd ?: "",
                tags = tags,
                loaded = true,
                isNew = entry == null
            )
            autoSaveEnabled = true

            // 输入停顿 1.5 秒后自动保存草稿
            _uiState
                .debounce { 1500L }
                .drop(1)
                .collect { state -> if (state.loaded) saveInternal(state) }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content)
    }

    fun setTags(tagNames: List<String>) {
        _uiState.value = _uiState.value.copy(tags = tagNames.distinct())
    }

    /** 页面退出时兜底保存 */
    fun saveOnExit() {
        val state = _uiState.value
        if (state.loaded) {
            viewModelScope.launch { saveInternal(state) }
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
        diaryRepository.setTagsForDiary(newId, state.tags)
        if (_uiState.value.entryId == null) {
            _uiState.value = _uiState.value.copy(entryId = newId, isNew = false)
        }
    }
}
