package com.example

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.components.MarkdownColumnAlignment
import com.example.ui.components.MarkdownTableParser
import com.example.ui.components.parseInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableParserTest {

    @Test
    fun `parses header alignment and body rows`() {
        val lines = listOf(
            "Before",
            "| Exercise | Reps | Load |",
            "| :--- | :---: | ---: |",
            "| **Squat** | 8 | 80 kg |",
            "| Bench | 10 | 60 kg |",
            "",
            "After"
        )

        val parsed = MarkdownTableParser.parse(lines, 1)!!
        val table = parsed.table
        assertEquals(listOf("Exercise", "Reps", "Load"), table.header)
        assertEquals(
            listOf(
                MarkdownColumnAlignment.START,
                MarkdownColumnAlignment.CENTER,
                MarkdownColumnAlignment.END
            ),
            table.alignments
        )
        assertEquals(2, table.rows.size)
        assertEquals(listOf("**Squat**", "8", "80 kg"), table.rows[0])
        assertEquals(5, parsed.nextIndex)
    }

    @Test
    fun `keeps an escaped pipe inside a cell`() {
        val table = MarkdownTableParser.parse(
            listOf(
                "| Lift | Notes |",
                "|---|---|",
                "| Carry | Left \\| right |"
            ),
            0
        )!!.table

        assertEquals(listOf("Carry", "Left | right"), table.rows.single())
    }

    @Test
    fun `pads short rows instead of losing columns`() {
        val table = MarkdownTableParser.parse(
            listOf(
                "| A | B | C |",
                "|---|---|---|",
                "| 1 | 2 |",
                "| 3 | 4 | 5 | 6 |"
            ),
            0
        )!!.table

        assertEquals(listOf("1", "2", ""), table.rows[0])
        assertEquals(listOf("3", "4", "5"), table.rows[1])
    }

    @Test
    fun `does not treat prose or a setext heading as a table`() {
        assertNull(
            MarkdownTableParser.parse(
                listOf("A normal paragraph", "with another line"),
                0
            )
        )
        assertNull(
            MarkdownTableParser.parse(
                listOf("Heading", "---"),
                0
            )
        )
        assertNull(
            MarkdownTableParser.parse(
                listOf("| Header |", "| not a separator |"),
                0
            )
        )
    }

    @Test
    fun `a table without body rows still parses its header`() {
        val parsed = MarkdownTableParser.parse(
            listOf(
                "| Name | Value |",
                "|---|---|",
                "Following paragraph"
            ),
            0
        )!!

        assertEquals(listOf("Name", "Value"), parsed.table.header)
        assertTrue(parsed.table.rows.isEmpty())
        assertEquals(2, parsed.nextIndex)
    }

    @Test
    fun `inline markdown styles bold italic strike and inline code`() {
        val styled = parseInlineMarkdown("**bold** *italic* ~~gone~~ `code`")

        assertEquals(4, styled.spanStyles.size)
        assertTrue(styled.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(styled.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(styled.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(styled.spanStyles.any { it.item.fontFamily != null })
    }

    @Test
    fun `triple markers produce bold italic and escaped markers stay literal`() {
        val both = parseInlineMarkdown("***both***")
        assertTrue(both.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic
        })

        val escaped = parseInlineMarkdown("\\*literal\\*")
        assertEquals("*literal*", escaped.text)
        assertTrue(escaped.spanStyles.isEmpty())
    }

    @Test
    fun `separator recognition requires a valid dash cell`() {
        assertTrue(MarkdownTableParser.isSeparatorCell("---"))
        assertTrue(MarkdownTableParser.isSeparatorCell(":---:"))
        assertTrue(MarkdownTableParser.isSeparatorCell(":---"))
        assertTrue(MarkdownTableParser.isSeparatorCell("---:"))
        assertTrue(!MarkdownTableParser.isSeparatorCell("not-a-separator"))
        assertTrue(!MarkdownTableParser.isSeparatorCell("--"))
    }
}
