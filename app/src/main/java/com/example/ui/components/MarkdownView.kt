package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Native Jetpack Compose Markdown Renderer for AI responses, Coach Advice, and Routines.
 * Formats Headings (H1-H4), Bullet Lists, Numbered Lists, Blockquotes, Bold, Italic, and Code spans.
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    baseTextColor: Color = TextWhite
) {
    val lines = markdown.lines()

    Column(modifier = modifier) {
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        var index = 0
        while (index < lines.size) {
            val rawLine = lines[index]
            val line = rawLine.trimEnd()

            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    CodeBlock(code = codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                index++
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(rawLine)
                index++
                continue
            }

            val parsedTable = MarkdownTableParser.parse(lines, index)
            if (parsedTable != null) {
                MarkdownTable(parsedTable.table, baseTextColor)
                index = parsedTable.nextIndex
                continue
            }

            val trimmed = line.trim()
            val listDepth = ((rawLine.length - rawLine.trimStart().length) / 2).coerceIn(0, 3)
            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                isHorizontalRule(trimmed) -> {
                    HorizontalDivider(
                        color = DarkBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.startsWith("###### ") -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("###### ").trim(), TextMuted),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                trimmed.startsWith("##### ") -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("##### ").trim(), TextMuted),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                trimmed.startsWith("#### ") -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("#### ").trim(), GoldenAmber),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = GoldenAmber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ").trim(), MintGreen),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MintGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                trimmed.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ").trim(), ElectricCyan),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                trimmed.startsWith("# ") -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ").trim(), TextWhite),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                    trimmed.startsWith("+ ") -> {
                    val bulletContent = trimmed.substring(2).trim()
                    BulletItem(content = bulletContent, baseColor = baseTextColor, depth = listDepth)
                }
                isNumberedItem(trimmed) -> {
                    val dotIdx = trimmed.indexOf('.')
                    val number = trimmed.substring(0, dotIdx).trim()
                    val content = trimmed.substring(dotIdx + 1).trim()
                    NumberedItem(
                        number = number,
                        content = content,
                        baseColor = baseTextColor,
                        depth = listDepth
                    )
                }
                trimmed.startsWith("> ") -> {
                    val quote = trimmed.removePrefix("> ").trim()
                    Blockquote(quote = quote, baseColor = baseTextColor)
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line, baseTextColor),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = baseTextColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            index++
        }

        // If a code block remained unclosed
        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlock(code = codeBlockLines.joinToString("\n"))
        }
    }
}

@Composable
private fun BulletItem(
    content: String,
    baseColor: Color,
    depth: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (4 + depth * 14).dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(if (depth > 0) 5.dp else 6.dp)
                .background(if (depth > 0) TextMuted else ElectricCyan, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = parseInlineMarkdown(content, baseColor),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = baseColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedItem(
    number: String,
    content: String,
    baseColor: Color,
    depth: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (4 + depth * 14).dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = ElectricCyan,
            modifier = Modifier.width(22.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = parseInlineMarkdown(content, baseColor),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = baseColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Blockquote(
    quote: String,
    baseColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .background(MintGreen, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = parseInlineMarkdown(quote, baseColor),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, lineHeight = 20.sp),
            color = TextMuted
        )
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = ElectricCyan
        )
    }
}

private fun isNumberedItem(line: String): Boolean {
    val dotIdx = line.indexOf('.')
    if (dotIdx in 1..4) {
        val prefix = line.substring(0, dotIdx)
        return prefix.all { it.isDigit() } && line.length > dotIdx + 1 && line[dotIdx + 1] == ' '
    }
    return false
}

private fun isHorizontalRule(line: String): Boolean {
    val compact = line.replace(" ", "")
    if (compact.length < 3) return false
    return compact.all { it == '-' } || compact.all { it == '*' } || compact.all { it == '_' }
}

@Composable
private fun MarkdownTable(table: MarkdownTableBlock, baseColor: Color) {
    val widths = remember(table) { tableColumnWidths(table) }
    val totalWidth = widths.fold(0.dp) { total, width -> total + width }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(totalWidth)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
        ) {
            MarkdownTableRow(
                cells = table.header,
                widths = widths,
                alignments = table.alignments,
                baseColor = baseColor,
                header = true
            )
            table.rows.forEachIndexed { index, row ->
                HorizontalDivider(color = DarkBorder.copy(alpha = 0.65f))
                MarkdownTableRow(
                    cells = row,
                    widths = widths,
                    alignments = table.alignments,
                    baseColor = baseColor,
                    header = false,
                    alternate = index % 2 == 1
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    widths: List<Dp>,
    alignments: List<MarkdownColumnAlignment>,
    baseColor: Color,
    header: Boolean,
    alternate: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    header -> DarkSurfaceVariant
                    alternate -> DarkSurfaceVariant.copy(alpha = 0.28f)
                    else -> Color.Transparent
                }
            )
    ) {
        cells.forEachIndexed { index, cell ->
            val alignment = alignments.getOrElse(index) { MarkdownColumnAlignment.START }
            Box(
                modifier = Modifier
                    .width(widths.getOrElse(index) { 84.dp })
                    .border(
                        width = 0.5.dp,
                        color = DarkBorder.copy(alpha = 0.45f)
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text(
                    text = parseInlineMarkdown(
                        cell,
                        if (header) ElectricCyan else baseColor
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = 18.sp
                    ),
                    color = if (header) ElectricCyan else baseColor,
                    textAlign = when (alignment) {
                        MarkdownColumnAlignment.START -> TextAlign.Start
                        MarkdownColumnAlignment.CENTER -> TextAlign.Center
                        MarkdownColumnAlignment.END -> TextAlign.End
                    }
                )
            }
        }
    }
}

private fun tableColumnWidths(table: MarkdownTableBlock): List<Dp> {
    if (table.header.isEmpty()) return emptyList()
    return table.header.indices.map { column ->
        val longest = (listOf(table.header[column]) + table.rows.map {
            it.getOrElse(column) { "" }
        }).maxOfOrNull { it.length } ?: 0
        // Character-count heuristic: wide enough for the content, narrow enough that a two- or
        // three-column table fits on a phone without dominating the summary. Very wide tables
        // scroll horizontally inside their own box, never the whole summary.
        (longest * 7 + 24).coerceIn(84, 240).dp
    }
}

fun parseInlineMarkdown(
    text: String,
    baseColor: Color = TextWhite
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            // Backslash escapes: the escaped character is shown literally.
            if (text[i] == '\\' && i + 1 < n) {
                append(text[i + 1])
                i += 2
                continue
            }

            // Bold + italic: ***text*** or ___text___
            if (i + 2 < n &&
                ((text.startsWith("***", i)) || (text.startsWith("___", i)))
            ) {
                val marker = text.substring(i, i + 3)
                val end = text.indexOf(marker, i + 3)
                if (end != -1) {
                    val content = text.substring(i + 3, end)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = baseColor
                        )
                    ) {
                        append(content)
                    }
                    i = end + 3
                    continue
                }
            }

            // Bold: **text** or __text__
            if (i + 1 < n &&
                ((text.startsWith("**", i)) || (text.startsWith("__", i)))
            ) {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(content)
                    }
                    i = end + 2
                    continue
                }
            }

            // Strikethrough: ~~text~~
            if (i + 1 < n && text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = baseColor
                        )
                    ) {
                        append(content)
                    }
                    i = end + 2
                    continue
                }
            }

            // Inline link: [label](url). The label is styled; the app does not open arbitrary URLs
            // from AI output.
            if (text[i] == '[') {
                val labelEnd = text.indexOf(']', i + 1)
                if (labelEnd != -1 && labelEnd + 1 < n && text[labelEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', labelEnd + 2)
                    if (urlEnd != -1) {
                        val label = text.substring(i + 1, labelEnd)
                        withStyle(
                            SpanStyle(
                                color = ElectricCyan,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(label)
                        }
                        i = urlEnd + 1
                        continue
                    }
                }
            }

            // Italic: *text* or _text_ (not part of a double marker)
            if ((text[i] == '*' || text[i] == '_') &&
                (i + 1 == n || text[i + 1] != text[i])
            ) {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end != -1 && (end + 1 == n || text[end + 1] != marker)) {
                    val content = text.substring(i + 1, end)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(content)
                    }
                    i = end + 1
                    continue
                }
            }

            // Check inline code: `code`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    val content = text.substring(i + 1, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan,
                            background = DarkSurfaceVariant
                        )
                    ) {
                        append(" $content ")
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
