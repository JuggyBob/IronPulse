package com.example

import com.example.data.export.ExportDocument
import com.example.data.export.ExportTable
import com.example.data.export.XlsxWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    @Test
    fun `xlsx is a valid zip with workbook and inline cells`() {
        val doc = ExportDocument(
            title = "Test",
            tables = listOf(
                ExportTable(
                    name = "Routine",
                    columns = listOf("Exercise", "Sets"),
                    rows = listOf(listOf("Push-ups", "3"), listOf("Rows & Pulls", "4"))
                )
            )
        )
        val bytes = ByteArrayOutputStream().use { out ->
            XlsxWriter.write(doc, out)
            out.toByteArray()
        }
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Push-ups"))
        assertTrue(sheet.contains("Rows &amp; Pulls"))
        assertTrue(sheet.contains("<v>3</v>"))
    }

    @Test
    fun `xlsx replaces xml-illegal control characters`() {
        val doc = ExportDocument(
            title = "Test",
            tables = listOf(
                ExportTable(
                    name = "Rows",
                    columns = listOf("Name"),
                    rows = listOf(listOf("Bench\u0000Press"), listOf("\u0001"))
                )
            )
        )
        val bytes = ByteArrayOutputStream().use { out ->
            XlsxWriter.write(doc, out)
            out.toByteArray()
        }
        val xmlParts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) {
                    xmlParts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        assertTrue(xmlParts.isNotEmpty())
        xmlParts.forEach { (name, xml) ->
            assertTrue("$name contains a raw NUL", !xml.contains('\u0000'))
            assertTrue("$name contains a raw SOH", !xml.contains('\u0001'))
            // Every XML part must still be well-formed after sanitising.
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        }
    }
}
