package com.mydiary.futureletter.ui.letters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mydiary.futureletter.core.database.entity.FutureLetter
import com.mydiary.futureletter.ui.components.MarkdownContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterListScreen(
    onOpenEditor: (letterId: Long?) -> Unit,
    viewModel: LetterListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("未来信") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenEditor(null) }) {
                Icon(Icons.Default.Add, contentDescription = "写一封未来信")
            }
        }
    ) { padding ->
        if (state.letters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✉️", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "还没有未来信",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "点右下角按钮，给未来的自己写一封信",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.letters, key = { it.id }) { letter ->
                    LetterCard(
                        letter = letter,
                        now = state.now,
                        onClick = { onOpenEditor(letter.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun LetterCard(letter: FutureLetter, now: Long, onClick: () -> Unit) {
    val unlocked = letter.isUnlocked || letter.unlockAt <= now

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (unlocked) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 标题始终可见
            Text(
                text = letter.title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (unlocked) {
                Text(
                    text = "🔓 已解锁 · ${formatUnlockTime(letter.unlockAt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 解锁后展示正文预览
                val plain = rememberPlainText(letter.contentMd)
                if (plain.isNotBlank()) {
                    Text(
                        text = plain,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 解锁前：只显示倒计时，正文隐藏
                Text(
                    text = "🔒 ${formatCountdown(letter.unlockAt - now)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "内容已封存，将于 ${formatUnlockTime(letter.unlockAt)} 解锁",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 去 Markdown 记号后的纯文本 */
private fun rememberPlainText(md: String): String =
    buildString {
        com.mydiary.futureletter.core.markdown.MarkdownParser.parse(md).forEach { block ->
            when (block) {
                is com.mydiary.futureletter.core.markdown.MdBlock.Code -> append(block.text)
                else -> block.parts
                    .filterIsInstance<com.mydiary.futureletter.core.markdown.MdPart.Span>()
                    .forEach { append(it.text) }
            }
            append(" ")
        }
    }.replace(Regex("\\s+"), " ").trim()
