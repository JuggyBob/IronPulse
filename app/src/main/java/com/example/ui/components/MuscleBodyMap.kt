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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
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
import com.example.ui.theme.ThemeRuntime

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

    /** A soft-cornered rectangle used for the segments of the abdominal wall. */
    fun block(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 0.9f) {
        val r = minOf(radius, (right - left) / 2f, (bottom - top) / 2f)
        move(left + r, top)
        line(right - r, top)
        cubic(right, top, right, top, right, top + r)
        line(right, bottom - r)
        cubic(right, bottom, right, bottom, right - r, bottom)
        line(left + r, bottom)
        cubic(left, bottom, left, bottom, left, bottom - r)
        line(left, top + r)
        cubic(left, top, left, top, left + r, top)
        close()
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
 * @param ops the anatomy, expressed in body units on the left side of the figure unless
 * [mirrored] is false, in which case it is already a full-width path.
 * @param hit the tap area, also in body units. Tapping either side of the centre line is tested.
 * @param mirrored whether the same path should also be drawn on the other side of the body.
 */
private data class MuscleShape(
    val part: MusclePart,
    val ops: List<BodyOp>,
    val hit: Rect,
    val mirrored: Boolean = true,
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

// ---------------------------------------------------------------------------
// Muscle anatomy
// ---------------------------------------------------------------------------

/**
 * The muscle groups of one view.
 *
 * Every region is drawn once on the left half and mirrored, which keeps the two sides perfectly
 * symmetrical and prevents the overlapping double shapes that made the earlier map read as a pile
 * of ovals. The paths follow the bellies of the muscles: fan-shaped pectorals, cap-like deltoids,
 * teardrop biceps and calves, a segmented abdominal wall, wing-like lats and rounded glutes.
 */
private fun muscleShapes(isFront: Boolean): List<MuscleShape> {
    val shapes = mutableListOf<MuscleShape>()
    fun add(
        part: MusclePart,
        hit: Rect,
        mirrored: Boolean = true,
        outlined: Boolean = true,
        build: BodyPath.() -> Unit
    ) {
        val path = BodyPath().apply(build)
        shapes += MuscleShape(part, path.ops, hit, mirrored, outlined)
    }

    if (isFront) {
        // Deltoids first, so the pectorals overlap their inner edge just as they do on a body.
        add(MusclePart.SHOULDERS, Rect(25.8f, 29.2f, 35.6f, 46.8f)) {
            move(35.6f, 30.6f)
            cubic(32.0f, 29.6f, 28.8f, 31.4f, 27.2f, 34.4f)
            cubic(25.8f, 37.2f, 25.8f, 41.0f, 27.0f, 43.8f)
            cubic(28.0f, 46.0f, 30.6f, 47.2f, 32.8f, 46.0f)
            cubic(35.0f, 44.8f, 36.2f, 41.8f, 36.2f, 38.4f)
            cubic(36.2f, 35.6f, 36.0f, 32.4f, 35.6f, 30.6f)
            close()
        }

        // Pectorals: a broad fan that follows the clavicle above and the sternum in the middle.
        add(MusclePart.CHEST, Rect(35.4f, 33.0f, 49.4f, 46.8f)) {
            move(48.8f, 32.8f)
            cubic(45.2f, 31.6f, 39.6f, 32.0f, 36.4f, 34.2f)
            cubic(34.2f, 35.8f, 33.6f, 38.4f, 34.4f, 40.8f)
            cubic(35.2f, 43.4f, 37.0f, 45.6f, 39.8f, 46.2f)
            cubic(42.4f, 46.8f, 45.4f, 45.8f, 47.2f, 44.2f)
            cubic(48.6f, 42.6f, 49.2f, 39.4f, 49.0f, 36.6f)
            cubic(49.0f, 35.2f, 49.0f, 33.6f, 48.8f, 32.8f)
            close()
        }

        // Biceps: a long belly that tapers into the elbow.
        add(MusclePart.ARMS, Rect(27.0f, 44.8f, 33.8f, 67.4f)) {
            move(33.4f, 45.4f)
            cubic(31.0f, 46.8f, 28.8f, 51.0f, 28.2f, 55.4f)
            cubic(27.8f, 59.2f, 28.4f, 63.4f, 29.8f, 66.2f)
            cubic(30.6f, 67.4f, 31.6f, 67.4f, 32.2f, 66.0f)
            cubic(33.4f, 63.4f, 34.0f, 58.4f, 33.8f, 53.6f)
            cubic(33.6f, 50.4f, 33.6f, 47.6f, 33.4f, 45.4f)
            close()
        }

        // Forearm flexors.
        add(MusclePart.FOREARMS, Rect(25.2f, 66.5f, 30.4f, 90.6f)) {
            move(30.0f, 67.2f)
            cubic(28.8f, 70.2f, 27.2f, 75.0f, 26.2f, 80.0f)
            cubic(25.6f, 84.0f, 25.6f, 87.6f, 26.2f, 89.6f)
            cubic(26.8f, 90.8f, 27.8f, 90.8f, 28.4f, 89.6f)
            cubic(29.4f, 87.0f, 30.0f, 82.4f, 30.0f, 77.4f)
            cubic(30.0f, 73.0f, 30.2f, 69.4f, 30.0f, 67.2f)
            close()
        }

        // External obliques: the flank between the ribs and the hip.
        add(MusclePart.CORE, Rect(36.8f, 47.0f, 44.4f, 71.8f)) {
            move(41.8f, 47.6f)
            cubic(39.2f, 50.4f, 37.8f, 55.2f, 38.0f, 60.0f)
            cubic(38.2f, 64.8f, 39.4f, 68.8f, 41.8f, 71.0f)
            cubic(43.0f, 66.0f, 43.0f, 58.0f, 43.0f, 53.0f)
            cubic(43.0f, 50.4f, 42.6f, 48.4f, 41.8f, 47.6f)
            close()
        }

        // Abdominals: one left column of three segments; the mirror completes the six pack.
        add(MusclePart.CORE, Rect(44.0f, 51.0f, 49.6f, 71.8f)) {
            block(44.6f, 51.8f, 49.4f, 57.6f)
            block(44.8f, 58.6f, 49.5f, 64.4f)
            block(45.0f, 65.4f, 49.5f, 71.4f)
        }

        // Quadriceps: rectus femoris and the two vasti as separate bellies.
        add(MusclePart.LEGS, Rect(38.6f, 94.0f, 46.4f, 124.5f)) {
            // rectus femoris
            move(44.6f, 94.8f)
            cubic(43.0f, 99.0f, 42.4f, 105.0f, 42.6f, 111.0f)
            cubic(42.8f, 116.0f, 43.2f, 120.0f, 43.8f, 121.8f)
            cubic(44.6f, 123.0f, 45.2f, 122.0f, 45.4f, 119.6f)
            cubic(45.8f, 114.0f, 46.0f, 106.0f, 45.6f, 99.0f)
            cubic(45.4f, 96.8f, 45.2f, 95.0f, 44.6f, 94.8f)
            close()
            // vastus lateralis
            move(40.8f, 95.6f)
            cubic(39.4f, 99.2f, 38.8f, 105.0f, 39.0f, 110.6f)
            cubic(39.2f, 115.0f, 39.6f, 118.6f, 40.2f, 120.2f)
            cubic(40.8f, 121.0f, 41.4f, 120.0f, 41.6f, 117.6f)
            cubic(42.0f, 112.0f, 42.0f, 104.0f, 41.8f, 99.2f)
            cubic(41.8f, 97.4f, 41.4f, 95.8f, 40.8f, 95.6f)
            close()
            // vastus medialis
            move(45.8f, 112.4f)
            cubic(46.2f, 116.4f, 46.0f, 120.2f, 45.4f, 122.8f)
            cubic(45.0f, 124.0f, 44.4f, 124.2f, 44.0f, 123.0f)
            cubic(43.8f, 120.0f, 44.0f, 116.2f, 44.4f, 113.0f)
            cubic(44.7f, 111.8f, 45.4f, 111.8f, 45.8f, 112.4f)
            close()
        }

        // Tibialis anterior: the long shin muscle, not another calf.
        add(MusclePart.LEGS, Rect(38.6f, 127.4f, 43.2f, 153.8f)) {
            move(41.0f, 128.0f)
            cubic(39.6f, 131.6f, 39.2f, 137.2f, 39.4f, 142.6f)
            cubic(39.6f, 147.8f, 40.0f, 151.8f, 40.6f, 153.4f)
            cubic(41.2f, 154.4f, 41.8f, 153.8f, 42.0f, 152.2f)
            cubic(42.6f, 147.0f, 42.8f, 140.8f, 42.6f, 135.8f)
            cubic(42.5f, 132.0f, 42.0f, 128.8f, 41.0f, 128.0f)
            close()
        }
    } else {
        // Trapezius: a full-width kite from the neck to the middle of the back.
        add(MusclePart.TRAPS, Rect(35.0f, 24.0f, 65.0f, 44.6f), mirrored = false) {
            move(50.0f, 24.2f)
            cubic(46.2f, 24.8f, 41.8f, 27.4f, 38.0f, 30.8f)
            cubic(36.0f, 32.6f, 35.2f, 35.0f, 36.6f, 36.6f)
            cubic(38.0f, 38.4f, 41.0f, 38.8f, 43.6f, 38.4f)
            cubic(45.8f, 41.8f, 48.0f, 43.4f, 50.0f, 44.2f)
            cubic(52.0f, 43.4f, 54.2f, 41.8f, 56.4f, 38.4f)
            cubic(59.0f, 38.8f, 62.0f, 38.4f, 63.4f, 36.6f)
            cubic(64.8f, 35.0f, 64.0f, 32.6f, 62.0f, 30.8f)
            cubic(58.2f, 27.4f, 53.8f, 24.8f, 50.0f, 24.2f)
            close()
        }

        // Latissimus dorsi: the wing that runs from the armpit to the lower back.
        add(MusclePart.BACK, Rect(34.8f, 37.0f, 44.8f, 74.0f)) {
            move(42.4f, 38.0f)
            cubic(39.0f, 40.0f, 36.2f, 44.6f, 35.4f, 50.0f)
            cubic(34.6f, 56.0f, 35.2f, 62.4f, 37.2f, 67.6f)
            cubic(38.8f, 71.2f, 41.0f, 73.2f, 43.6f, 73.6f)
            cubic(44.2f, 68.0f, 44.6f, 61.0f, 44.8f, 54.6f)
            cubic(45.0f, 47.8f, 44.6f, 41.2f, 42.4f, 38.0f)
            close()
        }

        // Rear deltoid.
        add(MusclePart.SHOULDERS, Rect(25.8f, 29.2f, 35.6f, 46.8f)) {
            move(35.6f, 30.6f)
            cubic(32.0f, 29.6f, 28.8f, 31.4f, 27.2f, 34.4f)
            cubic(25.8f, 37.2f, 25.8f, 41.0f, 27.0f, 43.8f)
            cubic(28.0f, 46.0f, 30.6f, 47.2f, 32.8f, 46.0f)
            cubic(35.0f, 44.8f, 36.2f, 41.8f, 36.2f, 38.4f)
            cubic(36.2f, 35.6f, 36.0f, 32.4f, 35.6f, 30.6f)
            close()
        }

        // Triceps.
        add(MusclePart.ARMS, Rect(27.0f, 44.8f, 33.8f, 67.4f)) {
            move(33.8f, 45.2f)
            cubic(31.2f, 46.4f, 28.8f, 50.8f, 28.2f, 55.4f)
            cubic(27.8f, 59.2f, 28.4f, 63.4f, 29.8f, 66.2f)
            cubic(30.6f, 67.4f, 31.6f, 67.4f, 32.2f, 66.0f)
            cubic(33.4f, 63.4f, 34.0f, 58.4f, 33.8f, 53.6f)
            cubic(33.6f, 50.4f, 33.8f, 47.4f, 33.8f, 45.2f)
            close()
        }

        // Forearm extensors.
        add(MusclePart.FOREARMS, Rect(25.2f, 66.5f, 30.4f, 90.6f)) {
            move(30.0f, 67.2f)
            cubic(28.8f, 70.2f, 27.2f, 75.0f, 26.2f, 80.0f)
            cubic(25.6f, 84.0f, 25.6f, 87.6f, 26.2f, 89.6f)
            cubic(26.8f, 90.8f, 27.8f, 90.8f, 28.4f, 89.6f)
            cubic(29.4f, 87.0f, 30.0f, 82.4f, 30.0f, 77.4f)
            cubic(30.0f, 73.0f, 30.2f, 69.4f, 30.0f, 67.2f)
            close()
        }

        // Erector spinae: the two narrow columns beside the spine.
        add(MusclePart.CORE, Rect(47.8f, 44.5f, 50.0f, 78.0f)) {
            move(49.2f, 45.2f)
            cubic(48.4f, 49.0f, 48.0f, 55.0f, 48.2f, 61.0f)
            cubic(48.4f, 68.0f, 48.6f, 74.0f, 49.0f, 76.8f)
            cubic(49.4f, 78.0f, 49.8f, 76.0f, 49.9f, 70.0f)
            cubic(50.0f, 59.0f, 49.8f, 50.0f, 49.2f, 45.2f)
            close()
        }

        // Gluteus maximus.
        add(MusclePart.GLUTES, Rect(36.8f, 75.0f, 49.8f, 94.8f)) {
            move(48.8f, 76.0f)
            cubic(44.4f, 75.6f, 40.2f, 77.6f, 38.2f, 81.6f)
            cubic(36.4f, 85.4f, 36.8f, 89.6f, 39.2f, 92.6f)
            cubic(41.6f, 95.2f, 45.6f, 95.4f, 48.4f, 92.6f)
            cubic(49.6f, 88.0f, 49.8f, 81.4f, 48.8f, 76.0f)
            close()
        }

        // Hamstrings: the outer and inner bellies.
        add(MusclePart.LEGS, Rect(38.2f, 93.6f, 47.0f, 125.2f)) {
            move(42.4f, 94.0f)
            cubic(39.8f, 98.2f, 38.4f, 105.0f, 38.6f, 112.0f)
            cubic(38.8f, 118.0f, 39.4f, 122.8f, 40.2f, 124.8f)
            cubic(40.8f, 125.8f, 41.6f, 124.8f, 41.8f, 121.8f)
            cubic(42.2f, 115.0f, 42.4f, 106.0f, 43.0f, 100.0f)
            cubic(43.2f, 97.0f, 43.0f, 94.8f, 42.4f, 94.0f)
            close()
            move(46.2f, 95.0f)
            cubic(46.8f, 100.0f, 47.0f, 108.0f, 46.6f, 115.0f)
            cubic(46.4f, 120.0f, 45.8f, 123.8f, 45.0f, 124.8f)
            cubic(44.2f, 125.6f, 43.6f, 124.0f, 43.6f, 121.0f)
            cubic(43.8f, 114.0f, 44.4f, 104.0f, 45.0f, 97.0f)
            cubic(45.2f, 95.4f, 45.8f, 94.8f, 46.2f, 95.0f)
            close()
        }

        // Gastrocnemius: two heads, medial and lateral.
        add(MusclePart.LEGS, Rect(37.8f, 127.0f, 47.8f, 153.2f)) {
            move(41.2f, 127.6f)
            cubic(39.4f, 130.2f, 38.2f, 135.0f, 38.2f, 140.0f)
            cubic(38.2f, 145.0f, 38.8f, 149.6f, 39.8f, 151.8f)
            cubic(40.4f, 152.8f, 41.0f, 151.8f, 41.2f, 149.0f)
            cubic(41.6f, 143.0f, 41.8f, 135.0f, 41.8f, 130.0f)
            cubic(41.8f, 128.4f, 41.6f, 127.6f, 41.2f, 127.6f)
            close()
            move(45.6f, 127.8f)
            cubic(47.2f, 130.6f, 47.8f, 136.0f, 47.4f, 141.0f)
            cubic(47.0f, 146.0f, 46.2f, 150.6f, 45.2f, 152.8f)
            cubic(44.6f, 153.8f, 44.0f, 152.6f, 43.8f, 150.0f)
            cubic(43.6f, 144.0f, 44.0f, 136.0f, 44.4f, 130.0f)
            cubic(44.6f, 128.4f, 45.2f, 127.8f, 45.6f, 127.8f)
            close()
        }
    }

    return shapes
}

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

private val BodySkin get() = ThemeRuntime.palette.muscle.skin
private val BodySkinLine get() = ThemeRuntime.palette.muscle.skinLine
private val MuscleRest get() = ThemeRuntime.palette.muscle.rest
private val MuscleDim get() = ThemeRuntime.palette.muscle.dim
private val MuscleLine get() = ThemeRuntime.palette.muscle.line

/** A lighter (dark theme) or deeper (light theme) version of the active accent. */
private val ActiveMuscleOutline: Color
    get() = lerp(
        ElectricCyan,
        if (ThemeRuntime.palette.isLight) Color.Black else Color.White,
        0.32f
    )

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
    modifier: Modifier = Modifier,
    activeParts: Set<MusclePart> = emptySet(),
    pendingParts: Set<MusclePart> = emptySet()
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
                activeParts = activeParts,
                pendingParts = pendingParts,
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
                activeParts = activeParts,
                pendingParts = pendingParts,
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
    activeParts: Set<MusclePart>,
    pendingParts: Set<MusclePart>,
    onSelect: ((MusclePart) -> Unit)?,
    maxFigureHeight: Dp,
    modifier: Modifier = Modifier
) {
    val currentOnSelect = rememberUpdatedState(onSelect)

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
                        if (hit != null) currentOnSelect.value?.invoke(hit.part)
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
                    isFront = isFront,
                    shapes = shapes,
                    highlighted = highlighted,
                    activeParts = activeParts,
                    pendingParts = pendingParts,
                    scale = scale,
                    offsetX = (size.width - BODY_WIDTH * scale) / 2f,
                    offsetY = (size.height - BODY_HEIGHT * scale) / 2f
                )
            }
        }
    }
}

/** Silhouette + muscle bellies. */
private fun DrawScope.drawFigure(
    isFront: Boolean,
    shapes: List<MuscleShape>,
    highlighted: MusclePart?,
    activeParts: Set<MusclePart>,
    pendingParts: Set<MusclePart>,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val lineWidth = (0.9f * scale).coerceAtLeast(1f)
    withTransform({ translate(offsetX, offsetY) }) {
        drawFigureBody(
            isFront = isFront,
            shapes = shapes,
            highlighted = highlighted,
            activeParts = activeParts,
            pendingParts = pendingParts,
            scaleX = scale,
            scaleY = scale,
            lineWidth = lineWidth
        )
    }
}

private fun DrawScope.drawFigureBody(
    isFront: Boolean,
    shapes: List<MuscleShape>,
    highlighted: MusclePart?,
    activeParts: Set<MusclePart>,
    pendingParts: Set<MusclePart>,
    scaleX: Float,
    scaleY: Float,
    lineWidth: Float
) {
    fun drawMirrored(
        build: BodyPath.() -> Unit,
        fill: Color,
        outline: Color,
        strokeWidth: Float = lineWidth
    ) {
        val left = BodyPath().apply(build).toPath(scaleX, scaleY)
        val right = BodyPath().apply(build).toPath(scaleX, scaleY, mirror = true)
        drawFull(left, fill, outline, strokeWidth)
        drawFull(right, fill, outline, strokeWidth)
    }

    // 1. Human silhouette: torso, separated arms and legs, neck, head and hands.
    drawFull(BodyPath().apply { torsoPath() }.toPath(scaleX, scaleY), BodySkin, BodySkinLine, lineWidth)
    drawMirrored({ armPath() }, BodySkin, BodySkinLine)
    drawMirrored({ legPath() }, BodySkin, BodySkinLine)
    drawNeck(scaleX, scaleY, BodySkin, BodySkinLine, lineWidth)
    drawHead(scaleX, scaleY)
    drawMirrored({ handPath() }, BodySkin, BodySkinLine)

    // 2. Muscle bellies on top of the skin.
    val activelyHighlighted = activeParts + setOfNotNull(highlighted)
    shapes.forEach { shape ->
        val isActive = shape.part in activelyHighlighted
        val isPending = !isActive && shape.part in pendingParts
        val fill = when {
            isActive -> ElectricCyan
            isPending -> ElectricCyan.copy(alpha = 0.34f)
            activelyHighlighted.isNotEmpty() || pendingParts.isNotEmpty() -> if (shape.outlined) {
                MuscleDim
            } else {
                ThemeRuntime.palette.muscle.inactiveFill
            }
            shape.outlined -> MuscleRest
            else -> ThemeRuntime.palette.muscle.absFill
        }
        val outline = when {
            isActive -> ActiveMuscleOutline
            isPending -> ElectricCyan.copy(alpha = 0.55f)
            else -> MuscleLine
        }
        drawMirrored(
            build = { shape.buildInto(this) },
            fill = fill,
            outline = outline,
            strokeWidth = if (shape.outlined) lineWidth else 0f
        )
    }

    // 3. A few quiet centre lines keep the sternum, linea alba and spine readable.
    drawBodyDetails(isFront, scaleX, scaleY, lineWidth)
}

/** Draws a single already complete path. */
private fun DrawScope.drawFull(path: Path, fill: Color, outline: Color, lineWidth: Float) {
    drawPath(path, fill)
    if (lineWidth > 0f) {
        drawPath(path, outline, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawBodyDetails(isFront: Boolean, scaleX: Float, scaleY: Float, lineWidth: Float) {
    val detailWidth = (lineWidth * 0.65f).coerceAtLeast(0.8f)
    val color = BodySkinLine.copy(alpha = 0.8f)
    fun detail(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(
            color = color,
            start = Offset(x1 * scaleX, y1 * scaleY),
            end = Offset(x2 * scaleX, y2 * scaleY),
            strokeWidth = detailWidth,
            cap = StrokeCap.Round
        )
    }
    if (isFront) {
        detail(50f, 32.6f, 50f, 45.8f)
        detail(50f, 50.8f, 50f, 71.8f)
    } else {
        detail(50f, 44.8f, 50f, 78.0f)
    }
}

private fun DrawScope.drawHead(scaleX: Float, scaleY: Float) {
    val head = BodyPath().apply {
        move(50f, 2.6f)
        cubic(43.8f, 2.6f, 41.4f, 7.0f, 41.5f, 11.8f)
        cubic(41.6f, 15.4f, 42.6f, 18.0f, 45.0f, 20.2f)
        cubic(47.0f, 21.8f, 49.0f, 22.2f, 50f, 22.2f)
        cubic(51.0f, 22.2f, 53.0f, 21.8f, 55.0f, 20.2f)
        cubic(57.4f, 18.0f, 58.4f, 15.4f, 58.5f, 11.8f)
        cubic(58.6f, 7.0f, 56.2f, 2.6f, 50f, 2.6f)
        close()
    }.toPath(scaleX, scaleY)
    drawPath(head, BodySkin)
    drawPath(
        head,
        BodySkinLine,
        style = Stroke(width = (0.9f * scaleX).coerceAtLeast(1f), join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawNeck(scaleX: Float, scaleY: Float, fill: Color, outline: Color, lineWidth: Float) {
    val neck = BodyPath().apply {
        move(45.8f, 20.2f)
        line(54.2f, 20.2f)
        cubic(54.4f, 23.2f, 53.8f, 25.8f, 53.0f, 27.4f)
        line(47.0f, 27.4f)
        cubic(46.2f, 25.8f, 45.6f, 23.2f, 45.8f, 20.2f)
        close()
    }.toPath(scaleX, scaleY)
    drawPath(neck, fill)
    drawPath(neck, outline, style = Stroke(width = lineWidth, join = StrokeJoin.Round))
}

/**
 * Torso only. The arms are separate shapes now, which leaves the small natural gap between the
 * inner arm and the flank visible instead of fusing them into one silhouette.
 */
private fun BodyPath.torsoPath() {
    move(45.6f, 26.0f)
    cubic(41.2f, 26.6f, 37.0f, 28.8f, 34.0f, 31.8f)
    cubic(32.0f, 33.8f, 31.2f, 36.6f, 31.8f, 39.2f)
    cubic(32.4f, 41.8f, 33.8f, 44.6f, 34.6f, 47.4f)
    cubic(35.2f, 52.0f, 36.0f, 57.0f, 36.8f, 62.0f)
    cubic(37.6f, 66.0f, 38.4f, 70.0f, 38.6f, 74.0f)
    cubic(38.8f, 79.0f, 38.4f, 85.0f, 39.4f, 90.0f)
    cubic(40.0f, 93.0f, 42.4f, 95.6f, 45.2f, 96.6f)
    cubic(47.0f, 97.2f, 49.0f, 97.2f, 50.0f, 97.2f)
    cubic(51.0f, 97.2f, 53.0f, 97.2f, 54.8f, 96.6f)
    cubic(57.6f, 95.6f, 60.0f, 93.0f, 60.6f, 90.0f)
    cubic(61.6f, 85.0f, 61.2f, 79.0f, 61.4f, 74.0f)
    cubic(61.6f, 70.0f, 62.4f, 66.0f, 63.2f, 62.0f)
    cubic(64.0f, 57.0f, 64.8f, 52.0f, 65.4f, 47.4f)
    cubic(66.2f, 44.6f, 67.6f, 41.8f, 68.2f, 39.2f)
    cubic(68.8f, 36.6f, 68.0f, 33.8f, 66.0f, 31.8f)
    cubic(63.0f, 28.8f, 58.8f, 26.6f, 54.4f, 26.0f)
    cubic(52.4f, 25.4f, 47.6f, 25.4f, 45.6f, 26.0f)
    close()
}

/** One arm, drawn on the left and mirrored. */
private fun BodyPath.armPath() {
    move(34.4f, 30.4f)
    cubic(31.6f, 30.6f, 28.8f, 32.6f, 27.4f, 36.0f)
    cubic(26.2f, 39.0f, 25.8f, 42.6f, 25.8f, 46.0f)
    cubic(25.6f, 51.0f, 25.2f, 56.0f, 25.4f, 60.4f)
    cubic(25.5f, 63.4f, 26.0f, 65.6f, 26.8f, 67.0f)
    cubic(25.6f, 70.0f, 24.6f, 74.6f, 24.4f, 79.0f)
    cubic(24.2f, 83.4f, 24.5f, 87.2f, 25.2f, 90.0f)
    line(29.6f, 90.4f)
    cubic(30.2f, 87.4f, 30.4f, 83.0f, 30.2f, 78.6f)
    cubic(30.0f, 74.0f, 29.8f, 70.0f, 30.8f, 67.0f)
    cubic(31.8f, 63.0f, 32.2f, 57.0f, 32.2f, 51.0f)
    cubic(32.2f, 45.0f, 32.8f, 38.0f, 34.4f, 30.4f)
    close()
}

/** One hand, drawn at the end of the arm. */
private fun BodyPath.handPath() {
    move(25.0f, 90.2f)
    cubic(24.0f, 92.0f, 23.8f, 94.4f, 24.6f, 96.0f)
    cubic(25.2f, 97.0f, 26.4f, 97.6f, 27.6f, 97.0f)
    cubic(29.2f, 96.0f, 30.0f, 94.0f, 29.8f, 92.0f)
    cubic(29.6f, 90.8f, 28.8f, 90.2f, 27.8f, 90.2f)
    cubic(26.8f, 90.2f, 25.8f, 90.2f, 25.0f, 90.2f)
    close()
}

/** One leg, drawn on the left and mirrored: thigh, knee, calf, ankle and foot. */
private fun BodyPath.legPath() {
    move(39.0f, 91.5f)
    cubic(37.4f, 98.0f, 36.6f, 105.0f, 36.8f, 112.0f)
    cubic(37.0f, 117.0f, 37.4f, 120.0f, 37.8f, 122.4f)
    cubic(37.0f, 125.0f, 36.4f, 129.0f, 36.6f, 133.0f)
    cubic(36.8f, 139.0f, 37.2f, 146.0f, 37.8f, 152.0f)
    cubic(38.2f, 155.0f, 38.2f, 157.4f, 37.6f, 159.0f)
    cubic(36.0f, 161.0f, 34.8f, 163.0f, 34.0f, 164.8f)
    cubic(33.8f, 165.8f, 34.4f, 166.4f, 35.6f, 166.4f)
    cubic(38.0f, 166.4f, 40.8f, 165.2f, 42.8f, 163.2f)
    cubic(44.0f, 162.0f, 44.2f, 159.8f, 44.0f, 156.8f)
    cubic(44.0f, 152.6f, 44.4f, 147.6f, 45.0f, 142.6f)
    cubic(45.8f, 136.0f, 46.4f, 129.0f, 46.8f, 123.0f)
    cubic(47.2f, 118.0f, 48.0f, 112.0f, 48.6f, 106.0f)
    cubic(49.2f, 100.0f, 50.0f, 96.0f, 50.0f, 93.0f)
    line(39.0f, 91.5f)
    close()
}
