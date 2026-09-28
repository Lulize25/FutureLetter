package com.mydiary.futureletter.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.backup.BackupManager
import com.mydiary.futureletter.core.settings.SettingsRepository
import com.mydiary.futureletter.ui.lock.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val message: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    /** 启动验证（指纹锁）开关状态 */
    val lockEnabled: StateFlow<Boolean> = settingsRepository.lockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !BiometricGate.canAuthenticate(context)) {
                _uiState.value = _uiState.value.copy(message = "无法开启：设备未录入指纹或未设置锁屏密码")
                return@launch
            }
            settingsRepository.setLockEnabled(enabled)
            _uiState.value = _uiState.value.copy(
                message = if (enabled) "已开启：下次启动及返回应用时需验证身份" else "已关闭身份验证"
            )
        }
    }

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
