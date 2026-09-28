package com.mydiary.futureletter.ui.lock

import androidx.lifecycle.ViewModel
import com.mydiary.futureletter.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class LockViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val lockEnabled: Flow<Boolean> = settingsRepository.lockEnabled
}
