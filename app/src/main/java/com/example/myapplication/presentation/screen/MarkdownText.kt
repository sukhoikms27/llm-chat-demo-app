package com.example.myapplication.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight Markdown renderer for Compose.
 * Supports: **bold**, *italic*, `inline code`, code blocks (```), headers (#/##/###),
 * bullet lists (-), numbered lists (1.)
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = parseMarkdownBlocks(markdown)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SelectionContainer(modifier = modifier) {
        Column {
            for (block in blocks) {
                when (block) {
                    is MarkdownBlock.CodeBlock -> {
                        Text(
                            text = block.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = codeTextColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(
                                    codeBackground,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(12.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    is MarkdownBlock.Heading -> {
                        val fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            else -> 16.sp
                        }
                        val fontWeight = when (block.level) {
                            1 -> FontWeight.Bold
                            else -> FontWeight.SemiBold
                        }
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                            ),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    is MarkdownBlock.ListItem -> {
                        val bullet = if (block.ordered) "${block.index}." else "•"
                        val styled = parseInlineStyles("$bullet ${block.text}")
                        Text(
                            text = styled,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    is MarkdownBlock.Paragraph -> {
                        if (block.text.isNotBlank()) {
                            val styled = parseInlineStyles(block.text)
                            Text(
                                text = styled,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Block-level parsing ──────────────────────────────────────────────────

private sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val content: String) : MarkdownBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        if (line.trimStart().startsWith("```")) {
            val fenceLen = line.trimStart().takeWhile { it == '`' }.length
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size) {
                val codeLine = lines[i]
                val closingFenceLen = codeLine.trimStart().takeWhile { it == '`' }.length
                if ((closingFenceLen >= fenceLen) && codeLine.trimStart().all { it == '`' }) {
                    i++
                    break
                }
                codeLines.add(codeLine)
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n")))
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
            blocks.add(MarkdownBlock.Heading(level, headingText))
            i++
            continue
        }

        val bulletMatch = Regex("^[-*]\\s+(.+)$").matchEntire(line)
        if (bulletMatch != null) {
            blocks.add(MarkdownBlock.ListItem(bulletMatch.groupValues[1], ordered = false, index = 0))
            i++
            continue
        }

        val orderedMatch = Regex("^(\\d+)[.)]\\s+(.+)$").matchEntire(line)
        if (orderedMatch != null) {
            val idx = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            blocks.add(MarkdownBlock.ListItem(orderedMatch.groupValues[2], ordered = true, index = idx))
            i++
            continue
        }

        if (line.isBlank()) {
            i++
            continue
        }

        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val pline = lines[i]
            if (pline.isBlank() || pline.trimStart().startsWith("```") ||
                Regex("^(#{1,6})\\s+").containsMatchIn(pline) ||
                Regex("^[-*]\\s+").containsMatchIn(pline) ||
                Regex("^(\\d+)[.)]\\s+").containsMatchIn(pline)
            ) break
            paraLines.add(pline)
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

// ── Inline styles ────────────────────────────────────────────────────────

@Composable
private fun parseInlineStyles(text: String): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex(
            """(?<code>`[^`\n]+?`)|""" +
                    """(?<bolditalic>\*\*\*.+?\*\*\*)|""" +
                    """(?<bold>\*\*.+?\*\*)|""" +
                    """(?<italic>\*.+?\*)|""" +
                    """(?<altbold>__.+?__)|""" +
                    """(?<altitalic>_.+?_)"""
        )

        var lastIndex = 0
        pattern.findAll(text).forEach { match ->
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }

            when {
                match.groups["code"] != null -> {
                    val content = match.groups["code"]!!.value.removeSurrounding("`")
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    ) { append(content) }
                }
                match.groups["bolditalic"] != null -> {
                    val content = match.groups["bolditalic"]!!.value.removeSurrounding("***")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
                match.groups["bold"] != null -> {
                    val content = match.groups["bold"]!!.value.removeSurrounding("**")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content) }
                }
                match.groups["italic"] != null -> {
                    val content = match.groups["italic"]!!.value.removeSurrounding("*")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(content) }
                }
                match.groups["altbold"] != null -> {
                    val content = match.groups["altbold"]!!.value.removeSurrounding("__")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content) }
                }
                match.groups["altitalic"] != null -> {
                    val content = match.groups["altitalic"]!!.value.removeSurrounding("_")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(content) }
                }
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
