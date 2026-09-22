package com.mydiary.futureletter.ui.letters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.database.entity.FutureLetter
import com.mydiary.futureletter.data.repository.LetterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LetterListUiState(
    val letters: List<FutureLetter> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

/** 把剩余毫秒格式化为中文倒计时 */
fun formatCountdown(remainingMillis: Long): String {
    if (remainingMillis <= 0) return "即将解锁"
    val totalMinutes = remainingMillis / 60000
    val days = totalMinutes / 1440
    val hours = totalMinutes % 1440 / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "还有 $days 天 $hours 小时"
        hours > 0 -> "还有 $hours 小时 $minutes 分"
        else -> "还有 $minutes 分钟"
    }
}

fun formatUnlockTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .let { java.time.format.DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm").format(it) }

@HiltViewModel
class LetterListViewModel @Inject constructor(
    letterRepository: LetterRepository
) : ViewModel() {

    /** 每秒跳一次的时间流，驱动倒计时刷新 */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    val uiState: StateFlow<LetterListUiState> =
        combine(letterRepository.observeAllLetters(), ticker) { letters, now ->
            LetterListUiState(letters = letters, now = now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LetterListUiState())
}
