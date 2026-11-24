package com.example

import com.example.data.export.ExportDocument
import com.example.data.export.ExportSection
import com.example.data.export.ExportTable
import com.example.data.export.PdfWriter
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfWriterTest {

    @Ignore("Robolectric's PdfDocument stub closes itself; PDF output is verified on the emulator.")
    @Test
    fun `pdf writer produces a real multi-section document`() {
        val doc = ExportDocument(
            title = "Routine",
            subtitle = "Test",
            sections = listOf(
                ExportSection.Heading("Exercises"),
                ExportSection.Paragraph("A printable training sheet."),
                ExportSection.Bullets(listOf("Step one", "Step two")),
                ExportSection.TableSection(
                    ExportTable(
                        "Sets",
                        listOf("Exercise", "Sets"),
                        listOf(listOf("Push-ups", "3"), listOf("Rows", "4"))
                    )
                )
            ),
            tables = listOf(ExportTable("Sets", listOf("Exercise", "Sets"), emptyList()))
        )
        val bytes = ByteArrayOutputStream().use { out ->
            PdfWriter.write(doc, out)
            out.toByteArray()
        }
        assertTrue(bytes.size > 100)
        assertTrue(bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII).startsWith("%PDF"))
    }
}
