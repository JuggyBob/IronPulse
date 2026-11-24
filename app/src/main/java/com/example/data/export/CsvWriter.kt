package com.example.data.export

/**
 * RFC 4180 CSV with spreadsheet formula-injection protection.
 *
 * Null stays an empty cell, which is distinct from the literal string "0". Text beginning with a
 * spreadsheet formula marker is prefixed with an apostrophe so opening the file cannot execute it.
 */
object CsvWriter {

    fun write(document: ExportDocument): String = buildString {
        appendCell(document.title)
        append("\r\n")
        if (document.subtitle.isNotBlank()) {
            appendCell(document.subtitle)
            append("\r\n")
        }
        append("\r\n")

        document.tables.forEachIndexed { index, table ->
            if (index > 0) append("\r\n")
            appendCell(table.name)
            append("\r\n")
            appendRow(table.columns)
            table.rows.forEach { appendRow(it) }
        }
    }

    private fun StringBuilder.appendRow(values: List<String?>) {
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendCell(value)
        }
        append("\r\n")
    }

    private fun StringBuilder.appendCell(value: String?) {
        if (value == null) return
        val safe = formulaSafe(value)
        val needsQuoting = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            safe.startsWith(' ') || safe.endsWith(' ')
        if (!needsQuoting) {
            append(safe)
        } else {
            append('"')
            append(safe.replace("\"", "\"\""))
            append('"')
        }
    }

    private fun formulaSafe(value: String): String {
        val trimmed = value.trimStart { character ->
            character.isWhitespace() ||
                character.code in 0x00..0x1F ||
                character.code in 0x7F..0x9F
        }
        return if (trimmed.startsWith("=") || trimmed.startsWith("+") ||
            trimmed.startsWith("-") || trimmed.startsWith("@")
        ) {
            "'" + value
        } else {
            value
        }
    }
}
