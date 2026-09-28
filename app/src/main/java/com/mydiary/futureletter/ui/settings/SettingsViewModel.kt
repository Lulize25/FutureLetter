package com.mydiary.futureletter.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val message: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun exportJson(target: Uri) =
        execute("导出完成：共 %d 篇日记") { backupManager.exportJson(target) }

    fun exportZip(target: Uri) =
        execute("导出完成：共 %d 篇日记") { backupManager.exportZip(target) }

    fun importFrom(source: Uri) =
        execute("导入完成：共恢复 %d 篇日记") { backupManager.importFrom(source) }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun execute(successFormat: String, block: suspend () -> Int) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(busy = true)
            try {
                val count = block()
                _uiState.value = SettingsUiState(message = String.format(successFormat, count))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = SettingsUiState(message = "操作失败：${e.message}")
            }
        }
    }
}
