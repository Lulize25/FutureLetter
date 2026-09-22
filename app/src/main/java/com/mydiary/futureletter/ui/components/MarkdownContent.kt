package com.mydiary.futureletter.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mydiary.futureletter.core.markdown.MdBlock
import com.mydiary.futureletter.core.markdown.MdPart
import com.mydiary.futureletter.core.markdown.MdSpanStyle
import com.mydiary.futureletter.core.markdown.MarkdownParser
import java.io.File

/**
 * 把 Markdown 文本渲染为 Compose 界面。
 * 图片路径为应用私有目录相对路径（如 images/xxx.jpg），绝对路径或 content uri 也可。
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val context = LocalContextHolder.current
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 28.sp
                        2 -> 24.sp
                        3 -> 20.sp
                        else -> 18.sp
                    }
                    MarkdownParts(
                        parts = block.parts,
                        style = TextStyle(fontSize = size, fontWeight = FontWeight.Bold),
                        context = context,
                        onLinkClick = onLinkClick
                    )
                }
                is MdBlock.Paragraph -> {
                    MarkdownParts(parts = block.parts, style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp), context = context, onLinkClick = onLinkClick)
                }
                is MdBlock.BulletItem -> {
                    MarkdownParts(
                        parts = listOf(MdPart.Span(block.marker + " ")) + block.parts,
                        style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                        context = context,
                        onLinkClick = onLinkClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                is MdBlock.OrderedItem -> {
                    MarkdownParts(
                        parts = listOf(MdPart.Span("${block.number}. ")) + block.parts,
                        style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                        context = context,
                        onLinkClick = onLinkClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                is MdBlock.Quote -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        MarkdownParts(
                            parts = block.parts,
                            style = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            context = context,
                            onLinkClick = onLinkClick
                        )
                    }
                }
                is MdBlock.Code -> {
                    Text(
                        text = block.text,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.small
                            )
                            .padding(12.dp)
                    )
                }
                is MdBlock.Table -> {
                    Text(
                        text = block.rows.joinToString("\n"),
                        style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
                MdBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

// 避免在多个 @Composable 参数里反复传 LocalContext.current
private object LocalContextHolder {
    val current: Context
        @Composable
        get() = androidx.compose.ui.platform.LocalContext.current
}

private sealed interface Segment {
    data class Text(val text: AnnotatedString) : Segment
    data class Image(val destination: String, val alt: String) : Segment
}

@Composable
private fun MarkdownParts(
    parts: List<MdPart>,
    style: TextStyle,
    context: Context,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 预分组：连续 Span 合并成一个 AnnotatedString，Image 独立成段
    val segments = remember(parts, style, onLinkClick) {
        val result = mutableListOf<Segment>()
        var spanBuffer = mutableListOf<MdPart.Span>()
        fun flush() {
            if (spanBuffer.isNotEmpty()) {
                result.add(Segment.Text(buildAnnotatedString {
                    appendSpans(spanBuffer, style, onLinkClick)
                }))
                spanBuffer = mutableListOf()
            }
        }
        parts.forEach { p ->
            when (p) {
                is MdPart.Span -> spanBuffer.add(p)
                is MdPart.Image -> {
                    flush()
                    result.add(Segment.Image(p.destination, p.alt))
                }
            }
        }
        flush()
        result
    }

    Column(modifier = modifier) {
        segments.forEach { seg ->
            when (seg) {
                is Segment.Text -> Text(
                    text = seg.text,
                    style = style,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                is Segment.Image -> {
                    val model = remember(seg.destination) { resolveImageModel(context, seg.destination) }
                    AsyncImage(
                        model = model,
                        contentDescription = seg.alt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendSpans(
    spans: List<MdPart.Span>,
    baseStyle: TextStyle,
    onLinkClick: (String) -> Unit
) {
    val linkColor = androidx.compose.ui.graphics.Color(0xFF6B4F9E)
    spans.forEach { span ->
        val spanStyle = SpanStyle(
            fontWeight = if (MdSpanStyle.BOLD in span.styles) FontWeight.Bold else baseStyle.fontWeight,
            fontStyle = if (MdSpanStyle.ITALIC in span.styles) FontStyle.Italic else baseStyle.fontStyle,
            fontFamily = if (MdSpanStyle.CODE in span.styles) FontFamily.Monospace else baseStyle.fontFamily,
            fontSize = if (MdSpanStyle.CODE in span.styles) (baseStyle.fontSize.value - 2).sp else baseStyle.fontSize,
            background = if (MdSpanStyle.CODE in span.styles)
                androidx.compose.ui.graphics.Color(0x33808080) else androidx.compose.ui.graphics.Color.Unspecified,
            color = if (span.link != null) linkColor else androidx.compose.ui.graphics.Color.Unspecified
        )
        if (span.link != null) {
            withLink(
                LinkAnnotation.Url(
                    span.link,
                    TextLinkStyles(style = spanStyle),
                    linkInteractionListener = { link ->
                        val url = (link as? LinkAnnotation.Url)?.url
                        if (url != null) onLinkClick(url)
                    }
                )
            ) {
                withStyle(spanStyle) { append(span.text) }
            }
        } else {
            withStyle(spanStyle) { append(span.text) }
        }
    }
}

private fun resolveImageModel(context: Context, destination: String): Any {
    return when {
        destination.startsWith("images/") || destination.startsWith("files/") ->
            File(context.filesDir, destination)
        destination.startsWith("/") -> File(destination)
        destination.startsWith("content://") -> Uri.parse(destination)
        else -> destination
    }
}
