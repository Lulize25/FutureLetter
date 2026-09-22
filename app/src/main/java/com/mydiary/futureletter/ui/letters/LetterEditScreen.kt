package com.mydiary.futureletter.ui.letters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mydiary.futureletter.ui.components.DatePickDialog
import com.mydiary.futureletter.ui.components.MarkdownContent
import com.mydiary.futureletter.ui.components.TimePickDialog
import com.mydiary.futureletter.ui.components.formatChinese

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterEditScreen(
    onBack: () -> Unit,
    viewModel: LetterEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var previewMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var contentField by remember {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.content.length)))
    }

    LaunchedEffect(state.loaded) {
        if (state.loaded) {
            contentField = TextFieldValue(state.content, TextRange(state.content.length))
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveOnExit() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "写一封未来信" else "编辑未来信") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveOnExit()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { previewMode = !previewMode }) {
                        Icon(
                            if (previewMode) Icons.Default.VisibilityOff else Icons.Default.RemoveRedEye,
                            contentDescription = if (previewMode) "返回编辑" else "预览"
                        )
                    }
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("这封信的标题（可留空）") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 解锁时间选择
            Card(
                onClick = { showDatePicker = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("解锁时间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.unlockAt.formatChinese(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    TextButton(onClick = { showTimePicker = true }) {
                        Text("调整时间")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (previewMode) {
                MarkdownContent(markdown = contentField.text, modifier = Modifier.fillMaxWidth())
            } else {
                BasicTextField(
                    value = contentField,
                    onValueChange = {
                        contentField = it
                        viewModel.updateContent(it.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { inner ->
                        if (contentField.text.isEmpty()) {
                            Text(
                                "亲爱的未来的我……\n（支持 Markdown 语法，右上角可预览）",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        DatePickDialog(
            initial = state.unlockAt.toLocalDate(),
            onConfirm = { date ->
                viewModel.updateUnlockAt(date.atTime(state.unlockAt.toLocalTime()))
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        TimePickDialog(
            initial = state.unlockAt.toLocalTime(),
            onConfirm = { time ->
                viewModel.updateUnlockAt(state.unlockAt.toLocalDate().atTime(time))
            },
            onDismiss = { showTimePicker = false }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这封信？") },
            text = { Text("删除后不可恢复，定时任务也会一并取消。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(onBack)
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}
