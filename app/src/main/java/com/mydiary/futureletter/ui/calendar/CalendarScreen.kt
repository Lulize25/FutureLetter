package com.mydiary.futureletter.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mydiary.futureletter.core.markdown.MarkdownParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月")

@Composable
fun CalendarScreen(
    onOpenEditor: (dateMillis: Long, entryId: Long?) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var showJumpDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 月份切换栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.changeMonth(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    text = state.currentMonth.format(MONTH_FORMAT),
                    style = MaterialTheme.typography.titleLarge
                )
                Row {
                    IconButton(onClick = { showJumpDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "跳转日期")
                    }
                    IconButton(onClick = { viewModel.changeMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
            }

            // 星期表头（周一开始）
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")
                weekDays.forEach { d ->
                    Text(
                        text = d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 月视图网格
            MonthGrid(
                month = YearMonth.from(state.currentMonth),
                selectedDate = state.selectedDate,
                today = today,
                datesWithEntries = state.datesWithEntries,
                onDateClick = { viewModel.selectDate(it) }
            )

            // 选中日期的日记摘要
            val entry = state.selectedEntry
            val millis = state.selectedDate
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable { onOpenEditor(millis, entry?.id) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.selectedDate.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE")),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (entry == null) {
                        Text(
                            text = "这一天还没有日记，点击开始记录 →",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = entry.title.ifBlank { "（无标题）" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val preview = rememberPlainPreview(entry.contentMd)
                        if (preview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val millis = state.selectedDate
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                onOpenEditor(millis, null)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = "写日记")
        }

        // 跳转日期
        if (showJumpDatePicker) {
            com.mydiary.futureletter.ui.components.DatePickDialog(
                initial = state.selectedDate,
                onConfirm = { viewModel.jumpToDate(it) },
                onDismiss = { showJumpDatePicker = false }
            )
        }
    }
}

/** 去掉 Markdown 记号后的纯文本预览 */
private fun rememberPlainPreview(md: String): String {
    val parts = MarkdownParser.parse(md)
    return buildString {
        parts.forEach { block ->
            when (block) {
                is com.mydiary.futureletter.core.markdown.MdBlock.Code -> append(block.text)
                else -> {
                    block.parts.filterIsInstance<com.mydiary.futureletter.core.markdown.MdPart.Span>()
                        .forEach { append(it.text) }
                }
            }
            append(" ")
        }
    }.replace(Regex("\\s+"), " ").trim()
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    datesWithEntries: Set<LocalDate>,
    onDateClick: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    // 周一=1 … 周日=7，计算网格开头的偏移
    var offset = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    Column(modifier = Modifier.fillMaxWidth()) {
        var index = 0
        while (index < totalCells) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = index + col
                    val dayNumber = cellIndex - offset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellIndex >= offset && dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val hasEntry = date in datesWithEntries

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isToday -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable { onDateClick(date) }
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                // 有日记的日期打点
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(
                                            color = if (hasEntry) MaterialTheme.colorScheme.primary
                                            else androidx.compose.ui.graphics.Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
            index += 7
        }
    }
}
