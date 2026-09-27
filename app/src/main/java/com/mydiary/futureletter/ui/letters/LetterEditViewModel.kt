package com.mydiary.futureletter.ui.letters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.database.entity.FutureLetter
import com.mydiary.futureletter.data.repository.LetterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class LetterEditUiState(
    val letterId: Long? = null,
    val title: String = "",
    val content: String = "",
    val unlockAt: LocalDateTime = LocalDateTime.now().plusDays(30),
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    /** 是否有未保存的修改 */
    val dirty: Boolean = false
)

@HiltViewModel
class LetterEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val letterRepository: LetterRepository
) : ViewModel() {

    private val navLetterId: Long? = savedStateHandle.get<String>("letterId")?.toLongOrNull()

    private val _uiState = MutableStateFlow(LetterEditUiState())
    val uiState: StateFlow<LetterEditUiState> = _uiState

    // 加载时的快照，用于判断是否有修改
    private var snapshotTitle: String = ""
    private var snapshotContent: String = ""
    private var snapshotUnlockAt: LocalDateTime? = null

    init {
        viewModelScope.launch {
            val letter = navLetterId?.let { letterRepository.getLetter(it) }
            val state = LetterEditUiState(
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
            takeSnapshot(state)
            _uiState.value = state
        }
    }

    private fun takeSnapshot(state: LetterEditUiState) {
        snapshotTitle = state.title
        snapshotContent = state.content
        snapshotUnlockAt = state.unlockAt
    }

    private fun computeDirty(s: LetterEditUiState): Boolean =
        s.loaded && (s.title != snapshotTitle ||
            s.content != snapshotContent ||
            s.unlockAt != snapshotUnlockAt)

    private fun currentStateWithDirty(update: LetterEditUiState.() -> LetterEditUiState) {
        val s = _uiState.value.update()
        _uiState.value = s.copy(dirty = computeDirty(s))
    }

    fun updateTitle(title: String) = currentStateWithDirty { copy(title = title) }

    fun updateContent(content: String) = currentStateWithDirty { copy(content = content) }

    fun updateUnlockAt(unlockAt: LocalDateTime) = currentStateWithDirty { copy(unlockAt = unlockAt) }

    /** 显式保存（确定键） */
    fun saveNow(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.loaded) {
            onSaved()
            return
        }
        viewModelScope.launch {
            saveInternal(state)
            // 保存成功（或内容为空未落库）后刷新快照
            takeSnapshot(_uiState.value)
            _uiState.value = _uiState.value.copy(dirty = computeDirty(_uiState.value))
            onSaved()
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
