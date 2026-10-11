package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.MusclePart
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted

/** Width of the anatomical drawing, in drawing units. */
private const val BODY_WIDTH = 100f

/** Height of the anatomical drawing, in drawing units (human proportion, 1 : 1.68). */
private const val BODY_HEIGHT = 168f

/** Horizontal centre line the body is mirrored around. */
private const val BODY_CENTER_X = 50f

// ---------------------------------------------------------------------------
// Path building in body units
// ---------------------------------------------------------------------------

private sealed interface BodyOp {
    data class Move(val x: Float, val y: Float) : BodyOp
    data class Line(val x: Float, val y: Float) : BodyOp
    data class Cubic(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float
    ) : BodyOp

    data object Close : BodyOp
}

/** Convenience builder so the anatomy below reads like a drawing, not like API calls. */
private class BodyPath {
    internal val ops = mutableListOf<BodyOp>()

    fun move(x: Float, y: Float) {
        ops += BodyOp.Move(x, y)
    }

    fun line(x: Float, y: Float) {
        ops += BodyOp.Line(x, y)
    }

    fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        ops += BodyOp.Cubic(x1, y1, x2, y2, x3, y3)
    }

    fun close() {
        ops += BodyOp.Close
    }

    /** Converts the body units into canvas pixels. */
    fun toPath(scaleX: Float, scaleY: Float, mirror: Boolean = false): Path {
        val out = Path()
        fun x(value: Float): Float =
            (if (mirror) BODY_CENTER_X * 2f - value else value) * scaleX

        ops.forEach { op ->
            when (op) {
                is BodyOp.Move -> out.moveTo(x(op.x), op.y * scaleY)
                is BodyOp.Line -> out.lineTo(x(op.x), op.y * scaleY)
                is BodyOp.Cubic -> out.cubicTo(
                    x(op.x1), op.y1 * scaleY,
                    x(op.x2), op.y2 * scaleY,
                    x(op.x3), op.y3 * scaleY
                )

                BodyOp.Close -> out.close()
            }
        }
        return out
    }
}

/**
 * One muscle group of the illustration.
 *
 * @param shape the belly that is drawn, expressed in body units.
 * @param hit the tap area, also in body units, so what is highlighted and what can be tapped are
 * two views of the same region.
 */
private data class MuscleShape(
    val part: MusclePart,
    val ops: List<BodyOp>,
    val hit: Rect,
    /** Small blocks (the abs) are filled without an outline, otherwise their own edges read as a grid. */
    val outlined: Boolean = true
) {
    /** The same anatomy, replayed into a fresh builder (the source of truth for both drawings). */
    fun buildInto(target: BodyPath) {
        ops.forEach { op ->
            when (op) {
                is BodyOp.Move -> target.move(op.x, op.y)
                is BodyOp.Line -> target.line(op.x, op.y)
                is BodyOp.Cubic -> target.cubic(op.x1, op.y1, op.x2, op.y2, op.x3, op.y3)
                BodyOp.Close -> target.close()
            }
        }
    }
}

/**
 * The muscle groups of one view.
 *
 * The coordinates describe a stylised but properly shaped male figure: a tapered torso with a waist,
 * deltoids that roll into the arms, an elbow break, knees and a calf belly that follows the line of
 * the tibia. Each muscle is a filled belly with its own outline rather than a plain rectangle or a
 * circle, which is what makes the map readable at phone size.
 */
private fun muscleShapes(isFront: Boolean): List<MuscleShape> {
    val shapes = mutableListOf<MuscleShape>()
    fun add(part: MusclePart, hit: Rect, outlined: Boolean = true, build: BodyPath.() -> Unit) {
        val path = BodyPath().apply(build)
        shapes += MuscleShape(part, path.ops, hit, outlined)
    }

    if (isFront) {
        // Pectorals: a rounded wedge under the clavicle, split by the sternum line
        add(MusclePart.CHEST, Rect(34f, 31.5f, 49.5f, 47f)) {
            move(48.4f, 32.6f)
            cubic(43.6f, 31.6f, 37.4f, 32.8f, 35.4f, 36.0f)
            cubic(33.6f, 39.2f, 35.2f, 44.6f, 39.4f, 45.8f)
            cubic(43.2f, 46.8f, 46.8f, 45.4f, 48.4f, 42.8f)
            cubic(49.6f, 40.4f, 49.4f, 34.0f, 48.4f, 32.6f)
            close()
        }

        // Deltoids
        add(MusclePart.SHOULDERS, Rect(25.6f, 28f, 39.8f, 47.6f)) {
            move(37.6f, 30.4f)
            cubic(33.0f, 28.4f, 28.2f, 31.2f, 27.0f, 35.8f)
            cubic(25.8f, 40.4f, 27.6f, 45.0f, 31.2f, 46.4f)
            cubic(34.4f, 47.6f, 37.6f, 45.2f, 38.6f, 41.4f)
            cubic(39.6f, 37.6f, 39.2f, 32.4f, 37.6f, 30.4f)
            close()
        }

        // Biceps
        add(MusclePart.ARMS, Rect(25.4f, 45.6f, 35.2f, 68.4f)) {
            move(30.6f, 46.4f)
            cubic(27.4f, 49.4f, 25.8f, 55.6f, 26.4f, 60.8f)
            cubic(27.0f, 65.4f, 29.4f, 68.6f, 32.0f, 67.6f)
            cubic(34.4f, 66.4f, 35.2f, 62.2f, 34.4f, 57.0f)
            cubic(33.6f, 52.0f, 32.6f, 48.2f, 30.6f, 46.4f)
            close()
        }

        // Forearms
        add(MusclePart.FOREARMS, Rect(22.8f, 67.6f, 31.4f, 93.8f)) {
            move(26.6f, 68.0f)
            cubic(24.4f, 72.8f, 23.2f, 80.4f, 23.6f, 86.6f)
            cubic(23.9f, 90.8f, 25.6f, 93.6f, 27.6f, 92.8f)
            cubic(30.0f, 91.6f, 31.0f, 86.2f, 30.6f, 80.0f)
            cubic(30.2f, 74.0f, 29.0f, 69.6f, 26.6f, 68.0f)
            close()
        }

        // Abdominals: two columns of three blocks
        val abs = listOf(
            Triple(47.4f, 55.4f, 3.4f), Triple(55.4f, 55.4f, 3.4f),
            Triple(47.6f, 62.8f, 3.2f), Triple(55.2f, 62.8f, 3.2f),
            Triple(47.9f, 69.6f, 2.9f), Triple(54.6f, 69.6f, 2.9f)
        )
        abs.forEach { (cx, cy, half) ->
            add(MusclePart.CORE, Rect(cx - half - 0.6f, cy - half - 1.2f, cx + half + 0.6f, cy + half), outlined = false) {
                move(cx - half, cy - half)
                cubic(cx - half, cy - half - 0.9f, cx + half, cy - half - 0.9f, cx + half, cy - half)
                cubic(cx + half + 0.5f, cy, cx + half, cy + half, cx - half, cy + half)
                cubic(cx - half - 0.5f, cy, cx - half, cy - half, cx - half, cy - half)
                close()
            }
        }

        // Obliques: the flanks of the abdomen
        add(MusclePart.CORE, Rect(36.2f, 46.4f, 43.6f, 70.4f), outlined = false) {
            move(40.6f, 47.2f)
            cubic(37.8f, 50.4f, 36.8f, 57.2f, 38.0f, 62.8f)
            cubic(39.0f, 67.2f, 41.0f, 69.6f, 43.2f, 70.0f)
            cubic(43.4f, 64.4f, 42.8f, 54.0f, 42.8f, 48.2f)
            close()
        }

        // Quadriceps
        add(MusclePart.LEGS, Rect(35.2f, 91.6f, 47.4f, 127f)) {
            move(41.4f, 92.2f)
            cubic(37.8f, 96.2f, 36.2f, 104.0f, 36.6f, 111.6f)
            cubic(37.0f, 118.6f, 38.8f, 124.6f, 41.4f, 125.8f)
            cubic(44.0f, 127.0f, 46.0f, 121.4f, 46.2f, 114.2f)
            cubic(46.4f, 106.8f, 45.2f, 98.8f, 43.2f, 93.0f)
            close()
        }

        // Calves
        add(MusclePart.LEGS, Rect(35.6f, 127.6f, 46.6f, 155.8f)) {
            move(40.8f, 128.2f)
            cubic(37.4f, 131.8f, 36.2f, 139.4f, 37.0f, 146.6f)
            cubic(37.6f, 151.6f, 39.4f, 154.8f, 41.6f, 155.0f)
            cubic(44.0f, 155.2f, 45.4f, 150.2f, 45.2f, 143.4f)
            cubic(45.0f, 137.0f, 43.6f, 130.6f, 41.8f, 128.6f)
            close()
        }
    } else {
        // Trapezius: the kite under the neck
        add(MusclePart.TRAPS, Rect(33.2f, 24.8f, 66.8f, 40f)) {
            move(50f, 25.2f)
            cubic(43.8f, 26.2f, 38.4f, 29.4f, 35.2f, 33.8f)
            cubic(33.2f, 36.4f, 34.4f, 39.2f, 37.6f, 38.6f)
            cubic(42.2f, 37.8f, 46.8f, 34.2f, 50f, 29.4f)
            cubic(53.2f, 34.2f, 57.8f, 37.8f, 62.4f, 38.6f)
            cubic(65.6f, 39.2f, 66.8f, 36.4f, 64.8f, 33.8f)
            cubic(61.6f, 29.4f, 56.2f, 26.2f, 50f, 25.2f)
            close()
        }

        // Latissimus: the wide wing of the back
        add(MusclePart.BACK, Rect(31.2f, 37.6f, 44.2f, 73.4f)) {
            move(35.8f, 40.2f)
            cubic(32.4f, 44.6f, 31.2f, 52.2f, 32.2f, 59.2f)
            cubic(33.2f, 66.2f, 36.2f, 71.4f, 40.6f, 72.8f)
            cubic(42.4f, 67.6f, 43.4f, 60.0f, 43.6f, 52.8f)
            cubic(43.8f, 46.4f, 43.4f, 41.4f, 42.4f, 38.4f)
            cubic(39.6f, 38.2f, 37.2f, 38.8f, 35.8f, 40.2f)
            close()
        }

        // Rear deltoids
        add(MusclePart.SHOULDERS, Rect(26.0f, 28f, 39.8f, 47.6f)) {
            move(37.4f, 30.6f)
            cubic(32.8f, 28.6f, 28.2f, 31.4f, 27.2f, 36.0f)
            cubic(26.2f, 40.6f, 28.2f, 45.2f, 31.8f, 46.4f)
            cubic(35.0f, 47.4f, 38.0f, 44.8f, 38.8f, 41.0f)
            cubic(39.6f, 37.2f, 39.2f, 32.4f, 37.4f, 30.6f)
            close()
        }

        // Triceps
        add(MusclePart.ARMS, Rect(26.2f, 45.6f, 35.6f, 68.4f)) {
            move(30.8f, 46.6f)
            cubic(27.8f, 49.6f, 26.4f, 55.8f, 27.0f, 61.0f)
            cubic(27.6f, 65.4f, 29.8f, 68.6f, 32.4f, 67.6f)
            cubic(34.8f, 66.2f, 35.4f, 61.8f, 34.6f, 56.6f)
            cubic(33.8f, 51.8f, 32.8f, 48.4f, 30.8f, 46.6f)
            close()
        }

        // Forearms
        add(MusclePart.FOREARMS, Rect(23.4f, 67.6f, 32f, 93.8f)) {
            move(27.0f, 68.2f)
            cubic(25.0f, 73.2f, 23.8f, 80.6f, 24.2f, 86.8f)
            cubic(24.5f, 91.0f, 26.2f, 93.6f, 28.2f, 92.8f)
            cubic(30.6f, 91.6f, 31.4f, 86.2f, 31.0f, 80.0f)
            cubic(30.6f, 74.2f, 29.4f, 69.8f, 27.0f, 68.2f)
            close()
        }

        // Glutes
        add(MusclePart.GLUTES, Rect(34.6f, 76.6f, 47.2f, 93.4f)) {
            move(41.4f, 77.0f)
            cubic(37.2f, 77.6f, 34.8f, 82.2f, 35.4f, 86.6f)
            cubic(36.0f, 90.6f, 39.2f, 93.0f, 42.4f, 92.6f)
            cubic(45.2f, 92.2f, 46.8f, 88.8f, 46.6f, 84.6f)
            cubic(46.4f, 80.6f, 44.6f, 77.6f, 41.4f, 77.0f)
            close()
        }

        // Hamstrings
        add(MusclePart.LEGS, Rect(35.2f, 93.6f, 47.4f, 129f)) {
            move(41.4f, 94.2f)
            cubic(37.8f, 98.2f, 36.2f, 106.0f, 36.6f, 113.6f)
            cubic(37.0f, 120.6f, 38.8f, 126.6f, 41.4f, 127.8f)
            cubic(44.0f, 129.0f, 46.0f, 123.4f, 46.2f, 116.2f)
            cubic(46.4f, 108.8f, 45.2f, 100.8f, 43.2f, 95.0f)
            close()
        }

        // Calves
        add(MusclePart.LEGS, Rect(35.6f, 129.6f, 46.6f, 157.8f)) {
            move(40.8f, 130.2f)
            cubic(37.4f, 133.8f, 36.2f, 141.4f, 37.0f, 148.6f)
            cubic(37.6f, 153.6f, 39.4f, 156.8f, 41.6f, 157.0f)
            cubic(44.0f, 157.2f, 45.4f, 152.2f, 45.2f, 145.4f)
            cubic(45.0f, 139.0f, 43.6f, 132.6f, 41.8f, 130.6f)
            close()
        }
    }

    return shapes
}

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

private val BodySkin = Color(0xFF39424E)
private val BodySkinLine = Color(0xFF4C5764)
private val MuscleRest = Color(0xFF3E4B5D)
private val MuscleDim = Color(0xFF2A323C)
private val MuscleLine = Color(0xFF566374)

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * Full body muscle map (male figure, front and back).
 *
 * @param highlighted the muscle group drawn in the accent colour; the others stay dimmed.
 * @param onSelect when null the illustration is a plain read-only picture: tapping does nothing.
 * That is the single exercise information popup, because there the body is tied to the muscle group
 * of that exercise instead of being a filter.
 * @param maxFigureHeight upper bound of the drawing, used where vertical space is tight: the figure
 * keeps its proportions and is centred instead of being stretched or clipped.
 */
@Composable
fun MuscleBodyMap(
    highlighted: MusclePart?,
    onSelect: ((MusclePart) -> Unit)? = null,
    maxFigureHeight: Dp = 430.dp,
    modifier: Modifier = Modifier
) {
    val frontShapes = remember { muscleShapes(true) }
    val backShapes = remember { muscleShapes(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("muscle_body_map")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BodyFigure(
                isFront = true,
                shapes = frontShapes,
                highlighted = highlighted,
                onSelect = onSelect,
                maxFigureHeight = maxFigureHeight,
                modifier = Modifier
                    .weight(1f)
                    .testTag("muscle_body_front")
            )
            Spacer(modifier = Modifier.width(6.dp))
            BodyFigure(
                isFront = false,
                shapes = backShapes,
                highlighted = highlighted,
                onSelect = onSelect,
                maxFigureHeight = maxFigureHeight,
                modifier = Modifier
                    .weight(1f)
                    .testTag("muscle_body_back")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                onSelect == null && highlighted != null -> "Muscles trained: ${highlighted.label}"
                onSelect == null -> "Muscles trained"
                else -> "Tap a muscle to filter the exercises"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BodyFigure(
    isFront: Boolean,
    shapes: List<MuscleShape>,
    highlighted: MusclePart?,
    onSelect: ((MusclePart) -> Unit)?,
    maxFigureHeight: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isFront) "FRONT" else "BACK",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        BoxWithConstraints {
            val figureWidth = maxWidth
            val naturalHeight = figureWidth * (BODY_HEIGHT / BODY_WIDTH)
            // A capped height keeps the proportions: the drawing is scaled down, never stretched
            val figureHeight = if (naturalHeight > maxFigureHeight) maxFigureHeight else naturalHeight
            val tap = if (onSelect == null) {
                Modifier
            } else {
                Modifier.pointerInput(shapes) {
                    detectTapGestures { tap ->
                        val scale = minOf(size.width / BODY_WIDTH, size.height / BODY_HEIGHT)
                        val offsetX = (size.width - BODY_WIDTH * scale) / 2f
                        val offsetY = (size.height - BODY_HEIGHT * scale) / 2f
                        val bodyX = (tap.x - offsetX) / scale
                        val bodyY = (tap.y - offsetY) / scale
                        val hit = shapes.firstOrNull { shape ->
                            shape.hit.contains(Offset(bodyX, bodyY)) ||
                                shape.hit.contains(Offset(BODY_CENTER_X * 2f - bodyX, bodyY))
                        }
                        if (hit != null) onSelect(hit.part)
                    }
                }
            }
            Canvas(
                modifier = tap
                    .fillMaxWidth()
                    .height(figureHeight)
                    .testTag(if (isFront) "body_canvas_front" else "body_canvas_back")
            ) {
                val scale = minOf(size.width / BODY_WIDTH, size.height / BODY_HEIGHT)
                drawFigure(
                    shapes = shapes,
                    highlighted = highlighted,
                    scale = scale,
                    offsetX = (size.width - BODY_WIDTH * scale) / 2f,
                    offsetY = (size.height - BODY_HEIGHT * scale) / 2f
                )
            }
        }
    }
}

/** Silhouette + muscle bellies. Every shape is drawn twice: once and mirrored. */
private fun DrawScope.drawFigure(
    shapes: List<MuscleShape>,
    highlighted: MusclePart?,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val lineWidth = (0.9f * scale).coerceAtLeast(1f)
    withTransform({ translate(offsetX, offsetY) }) {
        drawFigureBody(shapes, highlighted, scale, scale, lineWidth)
    }
}

private fun DrawScope.drawFigureBody(
    shapes: List<MuscleShape>,
    highlighted: MusclePart?,
    scaleX: Float,
    scaleY: Float,
    lineWidth: Float
) {
    fun drawPair(pathFactory: BodyPath.() -> Unit, fill: Color, outline: Color, outlined: Boolean = true) {
        val left = BodyPath().apply(pathFactory).toPath(scaleX, scaleY)
        val right = BodyPath().apply(pathFactory).toPath(scaleX, scaleY, mirror = true)
        drawPath(left, fill)
        drawPath(right, fill)
        if (outlined && lineWidth > 0f) {
            drawPath(left, outline, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(right, outline, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    // 1. Body silhouette, drawn as one continuous outline (no seam down the middle)
    drawFull(BodyPath().apply { torsoPath() }.toPath(scaleX, scaleY), BodySkin, BodySkinLine, lineWidth)
    drawFull(BodyPath().apply { legPath() }.toPath(scaleX, scaleY), BodySkin, BodySkinLine, lineWidth)
    drawHead(scaleX, scaleY)
    drawNeck(scaleX, scaleY, BodySkin, BodySkinLine, lineWidth)

    // 2. Muscle bellies on top of it, each one drawn on both sides of the body
    shapes.forEach { shape ->
        val isActive = highlighted == shape.part
        // Unoutlined blocks (the abs) are painted a shade lighter so the six pack reads as one group
        val fill = when {
            isActive -> ElectricCyan
            highlighted != null -> if (shape.outlined) MuscleDim else Color(0xFF323A45)
            shape.outlined -> MuscleRest
            else -> Color(0xFF45536A)
        }
        val outline = if (isActive) Color(0xFF8FF6FF) else MuscleLine
        drawPair({ shape.buildInto(this) }, fill, outline, shape.outlined)
    }
}

/** Draws a single already complete path. */
private fun DrawScope.drawFull(path: Path, fill: Color, outline: Color, lineWidth: Float) {
    drawPath(path, fill)
    drawPath(path, outline, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawHead(scaleX: Float, scaleY: Float) {
    val headWidth = 16.8f * scaleX
    val headHeight = 19f * scaleY
    val topLeft = Offset(41.6f * scaleX, 3.6f * scaleY)
    drawOval(color = BodySkin, topLeft = topLeft, size = Size(headWidth, headHeight))
    drawOval(
        color = BodySkinLine,
        topLeft = topLeft,
        size = Size(headWidth, headHeight),
        style = Stroke(width = (0.9f * scaleX).coerceAtLeast(1f))
    )
}

private fun DrawScope.drawNeck(scaleX: Float, scaleY: Float, fill: Color, outline: Color, lineWidth: Float) {
    val neck = BodyPath().apply {
        move(45.4f, 19.6f)
        line(54.6f, 19.6f)
        cubic(54.8f, 23.4f, 53.8f, 26.2f, 52.4f, 27.8f)
        line(47.6f, 27.8f)
        cubic(46.2f, 26.2f, 45.2f, 23.4f, 45.4f, 19.6f)
        close()
    }.toPath(scaleX, scaleY)
    drawPath(neck, fill)
    drawPath(neck, outline, style = Stroke(width = lineWidth))
}
/**
 * Torso and both arms as one outline.
 * It runs from the left side of the neck, over the left shoulder cap, down the outside of the left
 * arm with an elbow break, around the wrist, back up the inside of the arm, down the left flank to
 * the hip, across the hips, and then everything mirrored on the other side. Drawing it in one piece
 * is what keeps a seam from appearing down the middle of the body.
 */
private fun BodyPath.torsoPath() {
    move(45.8f, 26.4f)
    // ---- left shoulder cap ----
    cubic(40.0f, 27.0f, 33.6f, 28.4f, 29.8f, 31.6f)
    cubic(27.0f, 34.0f, 25.6f, 37.6f, 25.2f, 41.8f)
    // ---- left arm, outside down to the elbow ----
    cubic(24.6f, 47.6f, 23.8f, 53.8f, 23.2f, 59.6f)
    cubic(22.8f, 63.4f, 22.2f, 66.6f, 22.4f, 69.6f)
    // ---- forearm ----
    cubic(22.6f, 73.8f, 22.6f, 78.6f, 23.0f, 82.8f)
    cubic(23.2f, 86.2f, 23.6f, 89.0f, 24.6f, 90.6f)
    cubic(25.2f, 91.6f, 25.4f, 92.4f, 25.0f, 92.8f)
    // ---- hand tip ----
    cubic(24.4f, 92.0f, 24.0f, 90.0f, 24.0f, 87.4f)
    // ---- inside of the arm, back up ----
    cubic(23.8f, 82.6f, 24.2f, 78.2f, 25.0f, 74.6f)
    cubic(25.8f, 70.8f, 26.6f, 67.0f, 27.6f, 63.4f)
    cubic(28.8f, 59.0f, 30.0f, 54.4f, 31.2f, 50.2f)
    cubic(32.2f, 46.6f, 33.0f, 43.4f, 33.8f, 40.4f)
    // ---- armpit into the flank ----
    cubic(33.2f, 44.8f, 33.0f, 49.2f, 33.4f, 53.2f)
    cubic(34.0f, 59.0f, 35.2f, 65.0f, 36.6f, 70.6f)
    cubic(37.8f, 75.4f, 39.0f, 80.0f, 40.0f, 84.0f)
    cubic(40.8f, 87.0f, 41.4f, 89.6f, 41.8f, 91.6f)
    cubic(42.6f, 94.0f, 43.4f, 95.2f, 45.0f, 95.4f)
    // ---- hips ----
    line(50.0f, 95.8f)
    line(55.0f, 95.4f)
    // ---- mirrored side, back up ----
    cubic(56.6f, 95.2f, 57.4f, 94.0f, 58.2f, 91.6f)
    cubic(58.6f, 89.6f, 59.2f, 87.0f, 60.0f, 84.0f)
    cubic(61.0f, 80.0f, 62.2f, 75.4f, 63.4f, 70.6f)
    cubic(64.8f, 65.0f, 66.0f, 59.0f, 66.6f, 53.2f)
    cubic(67.0f, 49.2f, 66.8f, 44.8f, 66.2f, 40.4f)
    cubic(67.0f, 43.4f, 67.8f, 46.6f, 68.8f, 50.2f)
    cubic(70.0f, 54.4f, 71.2f, 59.0f, 72.4f, 63.4f)
    cubic(73.4f, 67.0f, 74.2f, 70.8f, 75.0f, 74.6f)
    cubic(75.8f, 78.2f, 76.2f, 82.6f, 76.0f, 87.4f)
    cubic(76.0f, 90.0f, 75.6f, 92.0f, 75.0f, 92.8f)
    cubic(74.6f, 92.4f, 74.8f, 91.6f, 75.4f, 90.6f)
    cubic(76.4f, 89.0f, 76.8f, 86.2f, 77.0f, 82.8f)
    cubic(77.4f, 78.6f, 77.4f, 73.8f, 77.6f, 69.6f)
    cubic(77.8f, 66.6f, 77.2f, 63.4f, 76.8f, 59.6f)
    cubic(76.2f, 53.8f, 75.4f, 47.6f, 74.8f, 41.8f)
    cubic(74.4f, 37.6f, 73.0f, 34.0f, 70.2f, 31.6f)
    cubic(66.4f, 28.4f, 60.0f, 27.0f, 54.2f, 26.4f)
    // ---- across the neck ----
    close()
}

/** Both legs as one outline: hips, thighs, knees, calves, ankles and feet. */
private fun BodyPath.legPath() {
    move(41.8f, 93.8f)
    // ---- left outer thigh ----
    cubic(39.8f, 99.8f, 37.6f, 107.2f, 37.0f, 113.8f)
    // ---- left knee ----
    cubic(36.6f, 118.0f, 36.8f, 121.6f, 37.4f, 124.8f)
    // ---- left calf ----
    cubic(38.2f, 130.0f, 38.4f, 136.2f, 38.2f, 142.4f)
    // ---- left ankle ----
    cubic(38.0f, 148.6f, 37.6f, 153.8f, 37.2f, 158.0f)
    // ---- left foot ----
    cubic(37.0f, 161.2f, 36.4f, 163.4f, 35.2f, 164.8f)
    cubic(34.6f, 165.6f, 34.4f, 166.2f, 34.8f, 166.4f)
    cubic(38.2f, 166.8f, 42.2f, 165.8f, 44.2f, 163.6f)
    cubic(45.2f, 162.4f, 45.4f, 160.4f, 45.4f, 157.8f)
    // ---- left inner leg up to the crotch ----
    cubic(45.4f, 152.8f, 46.0f, 147.8f, 46.6f, 142.8f)
    cubic(47.2f, 137.8f, 47.6f, 132.8f, 48.0f, 127.8f)
    cubic(48.4f, 123.8f, 49.2f, 119.6f, 50.0f, 116.4f)
    // ---- mirrored right leg ----
    cubic(50.8f, 119.6f, 51.6f, 123.8f, 52.0f, 127.8f)
    cubic(52.4f, 132.8f, 52.8f, 137.8f, 53.4f, 142.8f)
    cubic(54.0f, 147.8f, 54.6f, 152.8f, 54.6f, 157.8f)
    cubic(54.6f, 160.4f, 54.8f, 162.4f, 55.8f, 163.6f)
    cubic(57.8f, 165.8f, 61.8f, 166.8f, 65.2f, 166.4f)
    cubic(65.6f, 166.2f, 65.4f, 165.6f, 64.8f, 164.8f)
    cubic(63.6f, 163.4f, 63.0f, 161.2f, 62.8f, 158.0f)
    cubic(62.4f, 153.8f, 62.0f, 148.6f, 61.8f, 142.4f)
    cubic(61.6f, 136.2f, 61.8f, 130.0f, 62.6f, 124.8f)
    cubic(63.2f, 121.6f, 63.4f, 118.0f, 63.0f, 113.8f)
    cubic(62.4f, 107.2f, 60.2f, 99.8f, 58.2f, 93.8f)
    // ---- across the hips ----
    close()
}
