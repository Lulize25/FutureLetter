package com.mydiary.futureletter.ui.letters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.database.entity.FutureLetter
import com.mydiary.futureletter.data.repository.LetterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class LetterEditUiState(
    val letterId: Long? = null,
    val title: String = "",
    val content: String = "",
    val unlockAt: LocalDateTime = LocalDateTime.now().plusDays(30),
    val isNew: Boolean = true,
    val loaded: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LetterEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val letterRepository: LetterRepository
) : ViewModel() {

    private val navLetterId: Long? = savedStateHandle.get<String>("letterId")?.toLongOrNull()

    private val _uiState = MutableStateFlow(LetterEditUiState())
    val uiState: StateFlow<LetterEditUiState> = _uiState

    init {
        viewModelScope.launch {
            val letter = navLetterId?.let { letterRepository.getLetter(it) }
            _uiState.value = LetterEditUiState(
                letterId = letter?.id,
                title = letter?.title ?: "",
                content = letter?.contentMd ?: "",
                unlockAt = letter?.let {
                    java.time.Instant.ofEpochMilli(it.unlockAt)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                } ?: LocalDateTime.now().plusDays(30),
                isNew = letter == null,
                loaded = true
            )
            // 输入停顿 1.5 秒后自动保存（保存 = 入库 + 重新调度解锁任务）
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

    fun updateUnlockAt(unlockAt: LocalDateTime) {
        _uiState.value = _uiState.value.copy(unlockAt = unlockAt)
    }

    fun saveOnExit() {
        val state = _uiState.value
        if (state.loaded) {
            viewModelScope.launch { saveInternal(state) }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _uiState.value.letterId ?: return
        viewModelScope.launch {
            letterRepository.deleteLetter(id)
            onDone()
        }
    }

    private suspend fun saveInternal(state: LetterEditUiState) {
        // 标题正文都为空不落库
        if (state.title.isBlank() && state.content.isBlank()) return

        val now = System.currentTimeMillis()
        val title = state.title.ifBlank {
            state.content.lineSequence().firstOrNull { it.isNotBlank() }?.take(30)?.trim() ?: ""
        }
        val keptUnlocked = state.letterId
            ?.let { letterRepository.getLetter(it)?.isUnlocked }
            ?: false
        val letter = FutureLetter(
            id = state.letterId ?: 0,
            title = title,
            contentMd = state.content,
            unlockAt = state.unlockAt
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            createdAt = now,
            updatedAt = now,
            isUnlocked = keptUnlocked
        )
        val newId = letterRepository.saveLetter(letter)
        if (_uiState.value.letterId == null) {
            _uiState.value = _uiState.value.copy(letterId = newId, isNew = false)
        }
    }
}
