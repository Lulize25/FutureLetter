package com.mydiary.futureletter.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * 应用级偏好设置存储（DataStore）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lockEnabledKey = booleanPreferencesKey("lock_enabled")

    /** 是否开启启动验证（指纹/锁屏密码） */
    val lockEnabled: Flow<Boolean> = context.settingsStore.data.map { it[lockEnabledKey] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[lockEnabledKey] = enabled }
    }
}
