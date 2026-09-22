package com.mydiary.futureletter.ui.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mydiary.futureletter.ui.components.MarkdownContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryEditScreen(
    onBack: () -> Unit,
    viewModel: DiaryEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var previewMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var contentField by remember {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.content.length)))
    }

    // 内容加载完成后同步到输入框（仅初始一次）
    LaunchedEffect(state.loaded) {
        if (state.loaded) {
            contentField = TextFieldValue(state.content, TextRange(state.content.length))
        }
    }

    // 退出页面时兜底保存
    DisposableEffect(Unit) {
        onDispose { viewModel.saveOnExit() }
    }

    // 系统照片选择器（无需存储权限），选图后复制到私有目录并插入 Markdown
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val relativePath = copyImageToPrivateDir(context, uri)
            if (relativePath != null) {
                val snippet = "![图片]($relativePath)\n"
                val newText = contentField.text + snippet
                contentField = TextFieldValue(newText, TextRange(newText.length))
                viewModel.updateContent(newText)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isNew) "写日记" else "编辑日记",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
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
                placeholder = { Text("标题（可留空，自动取正文首行）") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 标签行
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.tags.forEach { tag ->
                    AssistChip(
                        onClick = { viewModel.setTags(state.tags - tag) },
                        label = { Text("$tag ✕") }
                    )
                }
                // 添加标签输入
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        modifier = Modifier.padding(vertical = 4.dp),
                        placeholder = { Text("添加标签") },
                        singleLine = true,
                        trailingIcon = {
                            if (newTagInput.isNotBlank()) {
                                IconButton(onClick = {
                                    viewModel.setTags(state.tags + newTagInput.trim())
                                    newTagInput = ""
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "添加标签")
                                }
                            }
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (previewMode) {
                MarkdownContent(
                    markdown = contentField.text,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Markdown 正文编辑
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
                                "用 Markdown 记录今天……\n支持 **粗体**、# 标题、- 列表、> 引用、`代码`",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 插入图片按钮
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("插入图片")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这篇日记？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(onBack)
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 把系统相册选中的图片复制到应用私有目录，返回相对路径 images/xxx */
private suspend fun copyImageToPrivateDir(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val ext = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            "images/${dest.name}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
