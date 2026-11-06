/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

/** Horizontal alignment declared by a Markdown table separator row. */
enum class MarkdownColumnAlignment { START, CENTER, END }

/** A parsed GitHub-flavoured Markdown table. */
data class MarkdownTableBlock(
    val header: List<String>,
    val alignments: List<MarkdownColumnAlignment>,
    val rows: List<List<String>>
)

/** A table found at [startIndex] in a document, plus the index after its last row. */
data class ParsedMarkdownTable(
    val table: MarkdownTableBlock,
    val nextIndex: Int
)

/**
 * Small, dependency-free GitHub-flavoured Markdown table reader.
 *
 * It deliberately understands only the table block: the caller still renders inline formatting in
 * every cell. Keeping the scanner pure makes malformed-table behaviour and alignment testable
 * without a Compose host.
 */
object MarkdownTableParser {

    private val separatorCell = Regex("^:?-{3,}:?$")

    fun parse(lines: List<String>, startIndex: Int): ParsedMarkdownTable? {
        val headerLine = lines.getOrNull(startIndex) ?: return null
        if (!containsUnescapedPipe(headerLine)) return null
        val header = splitRow(headerLine)
        if (header.size < 2) return null

        val separatorLine = lines.getOrNull(startIndex + 1) ?: return null
        if (!containsUnescapedPipe(separatorLine)) return null
        val separator = splitRow(separatorLine)
        if (separator.size < header.size || separator.any { !isSeparatorCell(it) }) return null

        val alignments = separator.take(header.size).map(::alignmentOf)
        val rows = mutableListOf<List<String>>()
        var index = startIndex + 2
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || !containsUnescapedPipe(line)) break
            val cells = splitRow(line)
            if (cells.isEmpty()) break
            rows.add(normalize(cells, header.size))
            index++
        }
        return ParsedMarkdownTable(
            MarkdownTableBlock(header, alignments, rows),
            index
        )
    }

    fun isSeparatorCell(cell: String): Boolean = separatorCell.matches(cell.trim())

    /** Splits a row on unescaped pipes and unescapes `\|` inside cell text. */
    fun splitRow(line: String): List<String> {
        val trimmed = line.trim()
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (char in trimmed) {
            when {
                escaped -> {
                    if (char == '|') {
                        current.append('|')
                    } else {
                        current.append('\\').append(char)
                    }
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '|' -> {
                    cells.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (escaped) current.append('\\')
        cells.add(current.toString().trim())

        if (trimmed.startsWith("|") && cells.firstOrNull()?.isEmpty() == true) {
            cells.removeAt(0)
        }
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|") &&
            cells.lastOrNull()?.isEmpty() == true
        ) {
            cells.removeAt(cells.lastIndex)
        }
        return cells
    }

    private fun normalize(cells: List<String>, columns: Int): List<String> =
        (0 until columns).map { index -> cells.getOrElse(index) { "" } }

    private fun alignmentOf(cell: String): MarkdownColumnAlignment {
        val trimmed = cell.trim()
        return when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> MarkdownColumnAlignment.CENTER
            trimmed.endsWith(":") -> MarkdownColumnAlignment.END
            else -> MarkdownColumnAlignment.START
        }
    }

    private fun containsUnescapedPipe(line: String): Boolean {
        var escaped = false
        for (char in line) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '|' -> return true
            }
        }
        return false
    }
}
