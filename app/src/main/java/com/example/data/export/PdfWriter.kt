package com.example.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max

/**
 * Print-friendly PDF writer built on the platform PdfDocument (API 19+, no dependency).
 *
 * The same [ExportDocument] is used by the on-screen preview, so pagination and content cannot
 * diverge between the preview and the saved file.
 */
object PdfWriter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val FOOTER = 28f
    private const val BODY_BOTTOM = PAGE_HEIGHT - MARGIN - FOOTER

    fun write(document: ExportDocument, output: OutputStream) {
        val pdf = PdfDocument()
        try {
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = textPaint(20f, Color.rgb(24, 28, 34), bold = true)
        val subtitlePaint = textPaint(10f, Color.rgb(90, 98, 108))
        val headingPaint = textPaint(14f, Color.rgb(24, 28, 34), bold = true)
        val bodyPaint = textPaint(9.5f, Color.rgb(36, 42, 50))
        val mutedPaint = textPaint(8f, Color.rgb(110, 118, 128))
        val linePaint = Paint().apply {
            color = Color.rgb(205, 212, 220)
            strokeWidth = 0.6f
        }
        val accent = Color.rgb(18, 112, 190)

        fun footer() {
            val footerPaint = textPaint(8f, Color.rgb(125, 132, 142))
            canvas.drawText("IronPulse", MARGIN, PAGE_HEIGHT - 20f, footerPaint)
            val label = pageNumber.toString()
            canvas.drawText(
                label,
                PAGE_WIDTH - MARGIN - footerPaint.measureText(label),
                PAGE_HEIGHT - 20f,
                footerPaint
            )
        }

        fun newPage() {
            footer()
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = MARGIN
        }

        fun ensure(height: Float) {
            if (y + height > BODY_BOTTOM) newPage()
        }

        fun wrappedLines(text: String, paint: Paint, width: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val words = text.replace("\r", "").split(Regex("\\s+")).filter { it.isNotBlank() }
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= width) {
                    line = StringBuilder(candidate)
                } else {
                    if (line.isNotEmpty()) lines.add(line.toString())
                    line = StringBuilder(word)
                }
            }
            if (line.isNotEmpty()) lines.add(line.toString())
            return lines.ifEmpty { listOf("") }
        }

        fun drawParagraph(
            text: String,
            paint: Paint,
            width: Float = PAGE_WIDTH - MARGIN * 2,
            leading: Float = 4f
        ) {
            val lineHeight = paint.textSize + leading
            wrappedLines(text, paint, width).forEach { line ->
                ensure(lineHeight)
                canvas.drawText(line, MARGIN, y + paint.textSize, paint)
                y += lineHeight
            }
        }

        fun drawBullets(items: List<String>) {
            items.forEach { item ->
                val bulletPaint = bodyPaint
                val indent = 12f
                val width = PAGE_WIDTH - MARGIN * 2 - indent
                val lines = wrappedLines(item, bulletPaint, width)
                val lineHeight = bulletPaint.textSize + 4f
                ensure(lineHeight * lines.size)
                canvas.drawCircle(MARGIN + 3f, y + bulletPaint.textSize * 0.55f, 1.7f, bulletPaint)
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, MARGIN + indent, y + bulletPaint.textSize, bulletPaint)
                    if (index < lines.lastIndex) y += lineHeight
                }
                y += lineHeight
            }
        }

        fun drawTable(table: ExportTable) {
            if (table.columns.isEmpty()) return
            val usable = PAGE_WIDTH - MARGIN * 2
            val columnWidth = usable / table.columns.size
            val headerHeight = 20f
            val rowPadding = 3f
            val lineHeight = bodyPaint.textSize + 2f

            fun drawHeader() {
                ensure(headerHeight)
                canvas.drawRect(
                    RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + headerHeight),
                    Paint().apply { color = Color.rgb(236, 241, 246) }
                )
                table.columns.forEachIndexed { index, column ->
                    canvas.drawText(
                        column,
                        MARGIN + index * columnWidth + rowPadding,
                        y + 13f,
                        textPaint(8f, Color.rgb(30, 38, 48), bold = true)
                    )
                }
                y += headerHeight
            }

            drawHeader()
            table.rows.forEach { row ->
                val cells = table.columns.indices.map { index ->
                    wrappedLines(row.getOrNull(index).orEmpty(), bodyPaint, columnWidth - rowPadding * 2)
                }
                val rowHeight = max(cells.maxOfOrNull { it.size } ?: 1, 1) * lineHeight + rowPadding * 2
                if (y + rowHeight > BODY_BOTTOM) {
                    newPage()
                    drawHeader()
                }
                cells.forEachIndexed { index, lines ->
                    lines.forEachIndexed { lineIndex, line ->
                        canvas.drawText(
                            line,
                            MARGIN + index * columnWidth + rowPadding,
                            y + rowPadding + lineHeight * (lineIndex + 1) - 1f,
                            bodyPaint
                        )
                    }
                }
                y += rowHeight
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            }
            y += 8f
        }

        fun drawImage(image: ExportImage) {
            val size = 70f
            ensure(size + 22f)
            val bitmap: Bitmap? = image.bitmap
            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                val drawWidth = if (ratio >= 1f) size else size * ratio
                val drawHeight = if (ratio >= 1f) size / ratio else size
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(MARGIN, y, MARGIN + drawWidth, y + drawHeight),
                    Paint().apply { isFilterBitmap = true }
                )
                y += drawHeight + 3f
            } else {
                canvas.drawRect(
                    RectF(MARGIN, y, MARGIN + size, y + size),
                    Paint().apply {
                        color = Color.rgb(241, 244, 247)
                        style = Paint.Style.FILL
                    }
                )
                canvas.drawText("No image", MARGIN + 10f, y + size / 2f, mutedPaint)
                y += size + 3f
            }
            image.attribution?.takeIf { it.isNotBlank() }?.let {
                ensure(12f)
                canvas.drawText(it, MARGIN, y + 8f, mutedPaint)
                y += 12f
            }
        }

        val columnGap = 14f
        val columnWidth = (PAGE_WIDTH - 2f * MARGIN - columnGap) / 2f
        var column = 0
        var columnTop = MARGIN
        val cardNamePaint = textPaint(11f, Color.rgb(24, 28, 34), bold = true)
        val cardBodyPaint = textPaint(8.2f, Color.rgb(36, 42, 50))
        val cardMutedPaint = textPaint(7.6f, Color.rgb(110, 118, 128))
        val cardAccentPaint = textPaint(7.4f, accent, bold = true)
        val imageSize = 54f
        val lineHeight = 9.8f

        fun ensureColumn(height: Float) {
            if (y + height <= BODY_BOTTOM) return
            if (column == 0) {
                column = 1
                y = columnTop
            } else {
                column = 0
                newPage()
                columnTop = MARGIN
                y = MARGIN
            }
        }

        fun blockHeight(block: ExportExerciseBlock, width: Float): Float {
            val hasImage = block.image?.bitmap != null
            val nameWidth = width - if (hasImage) imageSize + 8f else 0f
            val nameLines = wrappedLines(block.name, cardNamePaint, nameWidth).size
            var height = maxOf(if (hasImage) imageSize else 0f, nameLines * 12f) + 4f
            if (block.supersetLabel != null) height += 11f
            if (block.instructions.isNotEmpty()) {
                height += 13f
                block.instructions.forEach {
                    height += wrappedLines(it, cardBodyPaint, width - 12f).size * lineHeight + 2f
                }
            }
            if (block.sets.isNotEmpty()) {
                height += 13f
                block.sets.forEach {
                    height += wrappedLines(it.text, cardBodyPaint, width - if (it.isDrop) 12f else 0f)
                        .size * lineHeight + 2f
                }
            }
            return height + 14f
        }

        fun drawExerciseBlock(block: ExportExerciseBlock) {
                val height = blockHeight(block, columnWidth)
                val x = MARGIN + column * (columnWidth + columnGap)
                var localY = y
                val hasImage = block.image?.bitmap != null
                if (hasImage) {
                    val bitmap = block.image?.bitmap!!
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                    val drawWidth = if (ratio >= 1f) imageSize else imageSize * ratio
                    val drawHeight = if (ratio >= 1f) imageSize / ratio else imageSize
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(x, localY, x + drawWidth, localY + drawHeight),
                        Paint().apply { isFilterBitmap = true }
                    )
                }
                val nameX = x + if (hasImage) imageSize + 8f else 0f
                val nameWidth = columnWidth - if (hasImage) imageSize + 8f else 0f
                wrappedLines(block.name, cardNamePaint, nameWidth).take(3).forEach { line ->
                    canvas.drawText(line, nameX, localY + 10f, cardNamePaint)
                    localY += 12f
                }
                localY = maxOf(localY, y + if (hasImage) imageSize else 0f) + 2f
                block.supersetLabel?.let { label ->
                    canvas.drawText(label, x, localY + 8f, cardAccentPaint)
                    localY += 11f
                }
                if (block.instructions.isNotEmpty()) {
                    canvas.drawText("INSTRUCTIONS", x, localY + 8f, cardAccentPaint)
                    localY += 12f
                    block.instructions.forEach { instruction ->
                        val lines = wrappedLines(instruction, cardBodyPaint, columnWidth - 12f)
                        lines.forEachIndexed { index, line ->
                            if (index == 0) {
                                canvas.drawText("•", x + 1f, localY + 7f, cardMutedPaint)
                            }
                            canvas.drawText(line, x + 11f, localY + 7f, cardBodyPaint)
                            localY += lineHeight
                        }
                        localY += 2f
                    }
                }
                if (block.sets.isNotEmpty()) {
                    localY += 2f
                    canvas.drawText("SETS", x, localY + 8f, cardAccentPaint)
                    localY += 12f
                    block.sets.forEach { set ->
                        val indent = if (set.isDrop) 11f else 0f
                        val prefix = if (set.isDrop) "↳ " else "• "
                        val lines = wrappedLines(prefix + set.text, cardBodyPaint, columnWidth - indent)
                        lines.forEachIndexed { index, line ->
                            canvas.drawText(
                                line,
                                x + indent + if (index == 0) 0f else 7f,
                                localY + 7f,
                                if (set.isDrop) cardMutedPaint else cardBodyPaint
                            )
                            localY += lineHeight
                        }
                        localY += 2f
                    }
                }
                // A faint divider keeps two adjacent cards readable without reintroducing tables.
                canvas.drawLine(
                    x,
                    localY + 3f,
                    x + columnWidth,
                    localY + 3f,
                    Paint().apply {
                        color = Color.rgb(226, 231, 237)
                        strokeWidth = 0.6f
                    }
                )
                y += height
        }

        fun drawExerciseBlocks(blocks: List<ExportExerciseBlock>) {
            // Both columns start below the section heading, not at the page banner.
            columnTop = y
            var index = 0
            while (index < blocks.size) {
                val first = blocks[index]
                val groupId = first.supersetGroupId
                val group = if (groupId.isNullOrBlank()) {
                    listOf(first)
                } else {
                    val members = mutableListOf(first)
                    var next = index + 1
                    while (next < blocks.size && blocks[next].supersetGroupId == groupId) {
                        members += blocks[next]
                        next++
                    }
                    members
                }
                val heights = group.map { blockHeight(it, columnWidth) }
                val total = heights.sum()
                if (total <= BODY_BOTTOM - columnTop) {
                    // Never split a superset across the column gutter: A1 at the bottom of one
                    // column with A2 at the top of the next reads as an unrelated exercise.
                    ensureColumn(total)
                    group.forEach { drawExerciseBlock(it) }
                } else {
                    // Extremely long pair: flow normally rather than clip it off the page.
                    group.forEachIndexed { memberIndex, block ->
                        ensureColumn(heights[memberIndex])
                        drawExerciseBlock(block)
                    }
                }
                index += group.size
            }
        }

        // Header
        drawParagraph(document.title, titlePaint, leading = 6f)
        if (document.subtitle.isNotBlank()) {
            drawParagraph(document.subtitle, subtitlePaint)
        }
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply {
            color = accent
            strokeWidth = 1.4f
        })
        y += 14f

        document.sections.forEach { section ->
            when (section) {
                is ExportSection.Heading -> {
                    ensure(28f)
                    y += 4f
                    drawParagraph(section.text, headingPaint, leading = 4f)
                    y += 4f
                }
                is ExportSection.Paragraph -> drawParagraph(section.text, bodyPaint)
                is ExportSection.Bullets -> drawBullets(section.items)
                is ExportSection.TableSection -> drawTable(section.table)
                is ExportSection.ImageSection -> drawImage(section.image)
                is ExportSection.ExerciseBlocks -> drawExerciseBlocks(section.blocks)
                ExportSection.PageBreak -> newPage()
            }
        }

        footer()
        pdf.finishPage(page)
        pdf.writeTo(output)
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint = Paint().apply {
        textSize = size
        this.color = color
        isAntiAlias = true
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }
}
