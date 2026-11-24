package com.example.data.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML .xlsx writer.
 *
 * It deliberately avoids Apache POI: the app only needs a handful of tables, and a small inline
 * string writer keeps the APK and minSdk 24 build free of a desktop-sized dependency.
 */
object XlsxWriter {

    fun write(document: ExportDocument, output: OutputStream) {
        val sheets = document.tables.ifEmpty {
            listOf(ExportTable(document.title, listOf("Value"), emptyList()))
        }
        ZipOutputStream(output).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes(sheets.size))
            entry(zip, "_rels/.rels", rootRels())
            entry(zip, "xl/workbook.xml", workbook(sheets))
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry(zip, "xl/styles.xml", styles())
            sheets.forEachIndexed { index, table ->
                entry(zip, "xl/worksheets/sheet${index + 1}.xml", sheet(table))
            }
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    private fun workbook(sheets: List<ExportTable>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        sheets.forEachIndexed { index, table ->
            append("""<sheet name="${xml(sheetName(table.name, index))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun styles(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
            """</styleSheet>"""

    private fun sheet(table: ExportTable): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<cols>")
        table.columns.forEachIndexed { index, column ->
            val width = (column.length + 4).coerceIn(10, 42)
            append("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        append("<row r=\"1\">")
        table.columns.forEachIndexed { index, column ->
            append(inlineCell(index, 1, column, style = 1))
        }
        append("</row>")
        table.rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 2
            append("<row r=\"$r\">")
            table.columns.indices.forEach { columnIndex ->
                append(inlineCell(columnIndex, r, row.getOrNull(columnIndex), style = 0))
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun inlineCell(column: Int, row: Int, value: String?, style: Int): String {
        val ref = columnName(column) + row
        if (value == null) return """<c r="$ref" s="$style"/>"""
        val numeric = value.matches(Regex("""-?\d+(\.\d+)?"""))
        return if (numeric) {
            """<c r="$ref" s="$style"><v>$value</v></c>"""
        } else {
            """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${xml(value)}</t></is></c>"""
        }
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val out = StringBuilder()
        while (value > 0) {
            val modulo = (value - 1) % 26
            out.insert(0, ('A'.code + modulo).toChar())
            value = (value - 1) / 26
        }
        return out.toString()
    }

    private fun sheetName(raw: String, index: Int): String {
        val clean = raw.replace(Regex("""[\\/?*\[\]:]"""), " ").trim().ifBlank { "Sheet ${index + 1}" }
        return clean.take(31)
    }

    private fun xml(value: String): String = value
        .map { character ->
            val code = character.code
            when {
                code in 0x00..0x08 || code == 0x0B || code == 0x0C || code in 0x0E..0x1F ->
                    '\uFFFD'
                else -> character
            }
        }
        .joinToString("")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
