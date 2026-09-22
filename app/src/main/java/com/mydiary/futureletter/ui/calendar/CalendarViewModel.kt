package com.mydiary.futureletter.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydiary.futureletter.core.database.entity.DiaryEntry
import com.mydiary.futureletter.data.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val selectedDate: LocalDate = LocalDate.now(),
    val datesWithEntries: Set<LocalDate> = emptySet(),
    val selectedEntry: DiaryEntry? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CalendarUiState()
    )

    init {
        refreshMonthDots()
        // 选中日期变化时，观察对应日期的日记
        viewModelScope.launch {
            _uiState
                .map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val millis = date.toMillis()
                    diaryRepository.observeEntryByDate(millis)
                }
                .collect { entry ->
                    _uiState.value = _uiState.value.copy(selectedEntry = entry)
                }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun changeMonth(offset: Long) {
        val current = _uiState.value.currentMonth
        _uiState.value = _uiState.value.copy(
            currentMonth = current.plusMonths(offset).withDayOfMonth(1)
        )
        refreshMonthDots()
    }

    /** 跳转到任意日期：切换月份并选中该日 */
    fun jumpToDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            currentMonth = date.withDayOfMonth(1),
            selectedDate = date
        )
        refreshMonthDots()
    }

    private fun refreshMonthDots() {
        val month = _uiState.value.currentMonth
        val start = month.toMillis()
        val end = month.plusMonths(1).toMillis()
        viewModelScope.launch {
            diaryRepository.observeDatesInRange(start, end)
                .map { list -> list.map { millis -> millisToLocalDate(millis) }.toSet() }
                .collect { dates ->
                    _uiState.value = _uiState.value.copy(datesWithEntries = dates)
                }
        }
    }

    companion object {
        fun LocalDate.toMillis(): Long =
            atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun millisToLocalDate(millis: Long): LocalDate =
            java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
