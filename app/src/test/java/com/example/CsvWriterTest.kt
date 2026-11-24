package com.example

import com.example.data.export.CsvWriter
import com.example.data.export.ExportDocument
import com.example.data.export.ExportTable
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvWriterTest {

    @Test
    fun `csv escapes commas quotes and newlines and blocks formulas`() {
        val doc = ExportDocument(
            title = "Test",
            tables = listOf(
                ExportTable(
                    name = "Rows",
                    columns = listOf("Name", "Note", "Value"),
                    rows = listOf(
                        listOf("Bench, Press", "He said \"go\"", "0"),
                        listOf("=cmd()", "line\nbreak", "")
                    )
                )
            )
        )
        val csv = CsvWriter.write(doc)
        assertTrue(csv.contains("\"Bench, Press\""))
        assertTrue(csv.contains("\"He said \"\"go\"\"\""))
        assertTrue(csv.contains("'=cmd()"))
        assertTrue(csv.contains("\"line\nbreak\""))
    }

    @Test
    fun `csv blocks formulas hidden behind control characters`() {
        val doc = ExportDocument(
            title = "Test",
            tables = listOf(
                ExportTable(
                    name = "Rows",
                    columns = listOf("A", "B", "C"),
                    rows = listOf(
                        listOf("\u0000=1+1", "\u001B@SUM(A1)", "\t=1+1")
                    )
                )
            )
        )
        val csv = CsvWriter.write(doc)
        assertTrue(csv.contains("'\u0000=1+1"))
        assertTrue(csv.contains("'\u001B@SUM(A1)"))
        assertTrue(csv.contains("'\t=1+1"))
    }
}
