package com.mydiary.futureletter.core.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

// ---------- 解析结果的中间表示 ----------

enum class MdSpanStyle { BOLD, ITALIC, CODE }

sealed interface MdPart {
    data class Span(
        val text: String,
        val styles: Set<MdSpanStyle> = emptySet(),
        val link: String? = null
    ) : MdPart

    data class Image(val destination: String, val alt: String) : MdPart
}

sealed interface MdBlock {
    val parts: List<MdPart>

    data class Paragraph(override val parts: List<MdPart>) : MdBlock
    data class Heading(val level: Int, override val parts: List<MdPart>) : MdBlock
    data class BulletItem(val marker: String, override val parts: List<MdPart>) : MdBlock
    data class OrderedItem(val number: Int, override val parts: List<MdPart>) : MdBlock
    data class Quote(override val parts: List<MdPart>) : MdBlock
    data class Code(val text: String) : MdBlock {
        override val parts: List<MdPart> get() = emptyList()
    }
    data class Table(val rows: List<String>) : MdBlock {
        override val parts: List<MdPart> get() = emptyList()
    }
    data object Divider : MdBlock {
        override val parts: List<MdPart> get() = emptyList()
    }
}

/** 把 Markdown 文本解析为便于 Compose 渲染的块列表 */
object MarkdownParser {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    fun parse(markdown: String): List<MdBlock> {
        if (markdown.isBlank()) return emptyList()
        val document = parser.parse(markdown) as Document
        val blocks = mutableListOf<MdBlock>()
        var node: Node? = document.firstChild
        while (node != null) {
            parseBlock(node, blocks)
            node = node.next
        }
        return blocks
    }

    private fun parseBlock(node: Node, out: MutableList<MdBlock>) {
        when (node) {
            is Heading -> out.add(MdBlock.Heading(node.level, collectInline(node)))
            is Paragraph -> out.add(MdBlock.Paragraph(collectInline(node)))
            is BulletList -> {
                var child = node.firstChild
                while (child != null) {
                    out.add(MdBlock.BulletItem("•", collectInline(child)))
                    child = child.next
                }
            }
            is OrderedList -> {
                var child = node.firstChild
                var i = node.startNumber
                while (child != null) {
                    out.add(MdBlock.OrderedItem(i++, collectInline(child)))
                    child = child.next
                }
            }
            is BlockQuote -> out.add(MdBlock.Quote(collectInline(node)))
            is FencedCodeBlock -> out.add(MdBlock.Code(node.literal.trimEnd('\n')))
            is IndentedCodeBlock -> out.add(MdBlock.Code(node.literal.trimEnd('\n')))
            is TableBlock -> out.add(MdBlock.Table(collectTable(node)))
            is ThematicBreak -> out.add(MdBlock.Divider)
            else -> {
                // 未知块类型：尝试按段落处理其子内容
                val nested = mutableListOf<MdBlock>()
                var child = node.firstChild
                while (child != null) {
                    parseBlock(child, nested)
                    child = child.next
                }
                out.addAll(nested)
            }
        }
    }

    private fun collectTable(table: TableBlock): List<String> {
        val rows = mutableListOf<String>()
        var section: Node? = table.firstChild
        while (section != null) {
            var row: Node? = section.firstChild
            while (row != null) {
                if (row is TableRow) {
                    val cells = mutableListOf<String>()
                    var cell: Node? = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) {
                            cells.add(collectInline(cell).filterIsInstance<MdPart.Span>()
                                .joinToString("") { it.text })
                        }
                        cell = cell.next
                    }
                    rows.add(cells.joinToString(" | "))
                }
                row = row.next
            }
            section = section.next
        }
        return rows.filterIndexed { index, _ ->
            // 去掉 GFM 表头与正文之间的分隔行（形如 --- | ---）
            val s = rows.getOrNull(index) ?: return@filterIndexed true
            !(s.contains("---") && s.replace("|", "").replace("-", "").trim().isEmpty() && index == 1)
        }
    }

    /** 收集一个块节点下的所有行内元素 */
    private fun collectInline(node: Node): List<MdPart> {
        val parts = mutableListOf<MdPart>()
        val styles = mutableSetOf<MdSpanStyle>()
        collectInlineRecursive(node, parts, styles)
        return parts
    }

    private fun collectInlineRecursive(node: Node, out: MutableList<MdPart>, styles: MutableSet<MdSpanStyle>) {
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> out.add(MdPart.Span(child.literal, styles.toSet()))
                is Code -> {
                    out.add(MdPart.Span(child.literal, styles.toSet() + MdSpanStyle.CODE))
                }
                is Emphasis -> {
                    styles.add(MdSpanStyle.ITALIC)
                    collectInlineRecursive(child, out, styles)
                    styles.remove(MdSpanStyle.ITALIC)
                }
                is StrongEmphasis -> {
                    styles.add(MdSpanStyle.BOLD)
                    collectInlineRecursive(child, out, styles)
                    styles.remove(MdSpanStyle.BOLD)
                }
                is Link -> {
                    val dest = child.destination ?: ""
                    val before = out.size
                    collectInlineRecursive(child, out, styles)
                    // 给本次链接产生的 span 补上 link
                    for (i in before until out.size) {
                        val p = out[i]
                        if (p is MdPart.Span) out[i] = p.copy(link = dest)
                    }
                }
                is Image -> {
                    out.add(MdPart.Image(child.destination ?: "", child.title ?: ""))
                }
                is SoftLineBreak, is HardLineBreak -> {
                    out.add(MdPart.Span("\n", styles.toSet()))
                }
                else -> collectInlineRecursive(child, out, styles)
            }
            child = child.next
        }
    }
}
