package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
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
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletContent = trimmed.substring(2).trim()
                    BulletItem(content = bulletContent, baseColor = baseTextColor)
                }
                isNumberedItem(trimmed) -> {
                    val dotIdx = trimmed.indexOf('.')
                    val number = trimmed.substring(0, dotIdx).trim()
                    val content = trimmed.substring(dotIdx + 1).trim()
                    NumberedItem(number = number, content = content, baseColor = baseTextColor)
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
    baseColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(ElectricCyan, CircleShape)
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
    baseColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
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

fun parseInlineMarkdown(
    text: String,
    baseColor: Color = TextWhite
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            // Check bold: **bold**
            if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(content)
                    }
                    i = end + 2
                    continue
                }
            }
            // Check italic: *italic* (not followed by another *)
            if (text[i] == '*' && (i + 1 == n || text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && (end + 1 == n || text[end + 1] != '*')) {
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
