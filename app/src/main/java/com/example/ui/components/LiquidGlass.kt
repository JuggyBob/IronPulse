/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ThemeRuntime
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos

/**
 * Whether the Liquid Glass treatment is switched on.
 *
 * Every glass surface reads this instead of checking the preference itself, so the Settings switch
 * turns the whole treatment on and off in one place. The default is on: a fresh install should show
 * the finished look, and the setting is there for anyone who prefers the plain surfaces.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

/**
 * Shared geometry and falloff for the bottom bar magnification lens.
 *
 * The magnification is a continuous function of distance from the finger rather than a per-item
 * selection. That is what makes neighbouring destinations grow and shrink together as the finger
 * travels, instead of one icon popping while the others stay still.
 */
object LiquidGlassLens {
    /** How long the finger must stay down before the lens takes over from a normal tap. */
    const val ACTIVATION_DELAY_MS = 260L

    /** The largest scale at the centre of the lens. */
    const val MAX_SCALE = 1.26f

    /** How far the lens reaches, in dp. Neighbours inside this radius respond proportionally. */
    const val INFLUENCE_RADIUS_DP = 84f

    /** The visual radius of the glass lens itself, in dp. */
    const val LENS_RADIUS_DP = 40f

    /** How far an item lifts towards the finger at the centre of the lens, in dp. */
    const val LIFT_DP = 2f

    /**
     * A smooth 1-to-0 falloff over [influenceRadiusPx].
     *
     * The cosine curve has a flat top at the centre and a flat tail at the edge, so the lens has no
     * visible seam where its influence ends. Distances beyond the radius clamp to zero.
     */
    fun falloff(distancePx: Float, influenceRadiusPx: Float): Float {
        if (influenceRadiusPx <= 0f) return 0f
        val normalised = (distancePx / influenceRadiusPx).coerceIn(0f, 1f)
        return 0.5f * (1f + cos(PI.toFloat() * normalised))
    }

    /** The scale an item at [distancePx] from the lens centre should have. */
    fun scaleFor(
        distancePx: Float,
        influenceRadiusPx: Float,
        maxScale: Float = MAX_SCALE
    ): Float = 1f + (maxScale - 1f) * falloff(distancePx, influenceRadiusPx)

    /**
     * The destination under the finger when the lens is released.
     *
     * Uses the measured item bounds rather than dividing the bar into equal slots, so it survives
     * different screen widths, densities, spacing and future item counts. A release outside every
     * bound returns null and must not navigate.
     */
    fun itemIndexAt(position: Offset, itemBounds: List<Rect>): Int? {
        if (itemBounds.isEmpty()) return null
        return itemBounds.indexOfFirst { bounds ->
            position.x >= bounds.left && position.x <= bounds.right &&
                position.y >= bounds.top && position.y <= bounds.bottom
        }.takeIf { it >= 0 }
    }
}

/**
 * A translucent glass panel: a dark frosted base, a tinted refraction wash, a soft top sheen, a
 * delicate inner rim and a gradient border.
 *
 * It deliberately uses layered gradients rather than a blur library. The app supports API 24, where
 * Compose's blur has no effect, and a real backdrop blur would need an extra dependency plus a
 * capture of the content behind every panel. The layers below keep the cost to a handful of cheap
 * draw calls while still reading as glass against the dark background.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    enabled: Boolean,
    tint: Color = ElectricCyan,
    cornerRadius: Dp? = null,
    elevation: Dp = 14.dp,
    /**
     * True when the caller has already drawn a blurred capture of the content behind this surface.
     * The panel then uses a lighter body so the real backdrop remains visible instead of being
     * covered by an opaque gradient.
     */
    backdropBlurred: Boolean = false,
    /**
     * True for content-heavy panels such as the Customize Home sheet. These need a nearly opaque
     * body: a decorative alpha that lets the page show through makes long labels unreadable.
     */
    contentHeavy: Boolean = false
): Modifier {
    if (!enabled) return this
    val glass = ThemeRuntime.palette.glass
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = glass.shade.copy(alpha = 0.55f),
            spotColor = tint.copy(alpha = 0.20f)
        )
        .clip(shape)
        .drawBehind {
            drawLiquidGlassPanel(
                tint = tint,
                cornerRadiusPx = cornerRadius?.toPx() ?: (size.minDimension / 2f),
                backdropBlurred = backdropBlurred,
                contentHeavy = contentHeavy
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glass.borderStrong.copy(alpha = 0.34f),
                    glass.borderSoft.copy(alpha = 0.08f),
                    tint.copy(alpha = 0.24f),
                    glass.borderSoft.copy(alpha = 0.06f)
                )
            ),
            shape = shape
        )
}

/**
 * Draws a real blurred capture of the content behind a floating glass surface.
 *
 * The modifier draws the normal page, records a second copy of the content into a
 * [androidx.compose.ui.graphics.layer.GraphicsLayer], and then draws that copy clipped to [bounds]
 * with a RenderEffect blur. This is backdrop blur, not foreground blur: the pixels under the glass
 * are sampled from the page itself.
 *
 * RenderEffect blur is available from Android 12 (API 31). On older versions this modifier is a
 * no-op and the glass panel falls back to its translucent tint; the limitation is documented rather
 * than hidden behind a fake gradient.
 */
fun Modifier.liquidGlassBackdrop(
    enabled: Boolean,
    bounds: Rect?,
    cornerRadius: Dp,
    blurRadius: Dp = 14.dp
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return drawWithCache {
        val layer = obtainGraphicsLayer()
        val effect = BlurEffect(
            radiusX = blurRadius.toPx(),
            radiusY = blurRadius.toPx(),
            edgeTreatment = TileMode.Clamp
        )
        val clip = Path()
        val cornerPx = cornerRadius.toPx()

        onDrawWithContent {
            // Draw the page normally first.
            drawContent()

            val area = bounds
            if (area != null && area.width > 0f && area.height > 0f) {
                // Record a second copy for the glass. Its RenderEffect must stay set until the
                // frame executes: RenderNode properties are read when the display list runs, not
                // when drawLayer() is called. Resetting it here would make both copies unblurred.
                layer.record {
                    this@onDrawWithContent.drawContent()
                }
                layer.renderEffect = effect
                clip.reset()
                clip.addRoundRect(
                    RoundRect(
                        rect = area,
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )
                )
                clipPath(clip) {
                    // The layer is in the content's own coordinates. Clip it to the dock and draw
                    // it in place; the dock then shows the pixels that were actually underneath it.
                    drawLayer(layer)
                }
            }
        }
    }
}

private fun DrawScope.drawLiquidGlassPanel(
    tint: Color,
    cornerRadiusPx: Float,
    backdropBlurred: Boolean,
    contentHeavy: Boolean
) {
    val glass = ThemeRuntime.palette.glass

    // The frosted body. It is deliberately restrained: when a blurred backdrop has already been
    // captured, the body is only a tint over it. At the old 0.84-0.92 alpha the gradient covered
    // the page entirely, which is why the previous implementation read as a coloured slab rather
    // than glass. Content-heavy panels are the exception: long labels need the sheet to be
    // substantially opaque, so readability wins over seeing the page behind it.
    val bodyTop = when {
        contentHeavy -> 0.94f
        backdropBlurred -> 0.42f
        else -> 0.64f
    }
    val bodyMid = when {
        contentHeavy -> 0.96f
        backdropBlurred -> 0.50f
        else -> 0.72f
    }
    val bodyBottom = when {
        contentHeavy -> 0.98f
        backdropBlurred -> 0.58f
        else -> 0.80f
    }
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                glass.panelTop.copy(alpha = bodyTop),
                glass.panelMid.copy(alpha = bodyMid),
                glass.panelBottom.copy(alpha = bodyBottom)
            )
        )
    )

    // A restrained tint wash, stronger at the top left where the light enters.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                tint.copy(alpha = if (contentHeavy) 0.04f else if (backdropBlurred) 0.07f else 0.10f),
                Color.Transparent,
                tint.copy(alpha = if (contentHeavy) 0.02f else if (backdropBlurred) 0.03f else 0.05f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )

    // The broad specular sheen across the top half. It is drawn as an oval so the highlight fades
    // out before it reaches the lower edge instead of looking like a flat white stripe.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                glass.highlight.copy(alpha = if (contentHeavy) 0.09f else if (backdropBlurred) 0.13f else 0.17f),
                glass.highlight.copy(alpha = if (contentHeavy) 0.03f else if (backdropBlurred) 0.04f else 0.05f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.5f, -size.height * 0.15f),
            radius = size.width * 0.72f
        ),
        topLeft = Offset(-size.width * 0.1f, -size.height * 0.55f),
        size = Size(size.width * 1.2f, size.height * 0.95f)
    )

    // A soft inner shadow along the bottom edge gives the panel thickness without a hard line.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                glass.shade.copy(alpha = if (backdropBlurred) 0.14f else 0.20f)
            ),
            startY = size.height * 0.45f,
            endY = size.height
        )
    )

    // The inner rim catches the light just inside the border.
    drawRoundRect(
        color = glass.innerRim.copy(alpha = 0.06f),
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = Size(
            (size.width - 2.dp.toPx()).coerceAtLeast(0f),
            (size.height - 2.dp.toPx()).coerceAtLeast(0f)
        ),
        cornerRadius = CornerRadius(
            (cornerRadiusPx - 1.dp.toPx()).coerceAtLeast(0f),
            (cornerRadiusPx - 1.dp.toPx()).coerceAtLeast(0f)
        ),
        style = Stroke(width = 1.dp.toPx())
    )
}

/**
 * The glass lens that follows the finger across the bottom navigation bar.
 *
 * The icons themselves are magnified by their own graphics layers; this draws the translucent
 * bubble above them. The two together are what sell the effect: the glass looks like a physical
 * lens and the items beneath it genuinely grow as it passes.
 */
fun DrawScope.drawLiquidGlassLens(
    center: Offset,
    radiusPx: Float,
    progress: Float,
    tint: Color = ElectricCyan
) {
    if (progress <= 0.001f) return

    val glass = ThemeRuntime.palette.glass
    val radius = radiusPx * (0.88f + 0.12f * progress)
    val alpha = progress.coerceIn(0f, 1f)

    // Ambient halo. Kept low so the lens does not read as a glowing button.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                tint.copy(alpha = 0.16f * alpha),
                tint.copy(alpha = 0.05f * alpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 1.35f
        ),
        radius = radius * 1.35f,
        center = center
    )

    // The glass body. The highlight is offset towards the top left, which makes the surface look
    // curved rather than flat.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glass.lensHighlight.copy(alpha = 0.18f * alpha),
                glass.lensWash.copy(alpha = 0.10f * alpha),
                glass.lensMid.copy(alpha = 0.12f * alpha),
                glass.lensDeep.copy(alpha = 0.30f * alpha)
            ),
            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.40f),
            radius = radius * 1.55f
        ),
        radius = radius,
        center = center
    )

    // A faint inner ring reads as the edge of the refracting element.
    drawCircle(
        color = glass.lensHighlight.copy(alpha = 0.14f * alpha),
        radius = radius * 0.82f,
        center = Offset(center.x + radius * 0.06f, center.y + radius * 0.06f),
        style = Stroke(width = 1.dp.toPx())
    )

    // The luminous edge, with the tint running around it.
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                glass.lensHighlight.copy(alpha = 0.52f * alpha),
                tint.copy(alpha = 0.30f * alpha),
                glass.lensHighlight.copy(alpha = 0.10f * alpha),
                tint.copy(alpha = 0.26f * alpha),
                glass.lensHighlight.copy(alpha = 0.52f * alpha)
            ),
            center = center
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Specular arc on the upper left.
    drawArc(
        color = glass.lensHighlight.copy(alpha = 0.58f * alpha),
        startAngle = 200f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.94f, center.y - radius * 0.94f),
        size = Size(radius * 1.88f, radius * 1.88f),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
    )

    // A cooler caustic on the lower right balances the warm white highlight.
    drawArc(
        color = tint.copy(alpha = 0.40f * alpha),
        startAngle = 20f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.90f, center.y - radius * 0.90f),
        size = Size(radius * 1.80f, radius * 1.80f),
        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * Detects the press-and-hold gesture that drives the lens.
 *
 * The detector watches the Initial pass and only consumes events after the hold threshold has been
 * reached. A quick tap therefore reaches the normal navigation items untouched, while a held finger
 * has its movement and final lift consumed, so the item underneath cannot also fire a click.
 */
suspend fun PointerInputScope.detectLiquidGlassLensGesture(
    activationDelayMs: Long = LiquidGlassLens.ACTIVATION_DELAY_MS,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
    onCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val downPosition = down.position
        var lastPosition = downPosition
        var active = false
        var released = false

        try {
            while (true) {
                val elapsed = SystemClock.uptimeMillis() - down.uptimeMillis
                val remaining = activationDelayMs - elapsed
                val event = if (!active && remaining > 0L) {
                    withTimeoutOrNull(remaining) {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                } else {
                    awaitPointerEvent(PointerEventPass.Initial)
                }

                if (event == null) {
                    // The finger has stayed down for the whole threshold: take over the gesture.
                    active = true
                    onStart(lastPosition)
                    continue
                }

                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                lastPosition = change.position
                if (!change.pressed) {
                    // The release position is the destination. Consume the up so the item under
                    // the finger cannot also fire its normal click after a long press.
                    if (active) {
                        change.consume()
                        released = true
                    }
                    break
                }

                if (!active) {
                    // A finger that travels before the threshold is a swipe or a scroll, not a
                    // hold. Let the normal gesture handling have it.
                    if ((change.position - downPosition).getDistance() >
                        viewConfiguration.touchSlop
                    ) {
                        break
                    }
                } else {
                    onMove(change.position)
                    change.consume()
                }
            }
        } finally {
            if (active) {
                // A cancelled gesture (a parent taking over, the window losing focus) must not
                // navigate to whatever happened to be under the finger when it ended.
                if (released) onRelease(lastPosition) else onCancel()
            }
        }
    }
}

/**
 * Whether the system asks apps to avoid motion.
 *
 * The lens still tracks the finger, because that is direct manipulation rather than an animation,
 * but its appearance and dismissal are snapped when animations are switched off.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }
    }
}
