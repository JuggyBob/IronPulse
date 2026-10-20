package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.util.ImageCropper
import kotlin.math.max
import kotlin.math.min

/**
 * Lets the athlete frame their profile picture.
 *
 * A profile picture is shown in a circle, so the picture is cropped square. The athlete drags the
 * picture around and pinches it to zoom, and what they framed is what is kept.
 *
 * The picture always covers the square. Letting it drift away from the edges would leave gaps in
 * the circle, which is the one thing that looks broken on a profile.
 */
@Composable
fun AvatarCropper(
    source: Bitmap,
    onCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var viewportSize by remember { mutableStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // The scale at which the picture fills the square, so there are never gaps in the crop.
    val coverScale = remember(source, viewportSize) {
        if (viewportSize <= 0) 1f else max(
            viewportSize.toFloat() / source.width,
            viewportSize.toFloat() / source.height
        )
    }

    // The picture is drawn at exactly this size and then zoomed by the athlete, which keeps the
    // drawing and the crop in step: what is framed is what is cut out.
    val coverWidth = source.width * coverScale
    val coverHeight = source.height * coverScale

    fun clampOffset(candidate: Offset, currentZoom: Float): Offset {
        if (viewportSize <= 0) return Offset.Zero
        val size = viewportSize.toFloat()
        val drawnWidth = coverWidth * currentZoom
        val drawnHeight = coverHeight * currentZoom
        val minX = (size - drawnWidth) / 2f
        val maxX = (drawnWidth - size) / 2f
        val minY = (size - drawnHeight) / 2f
        val maxY = (drawnHeight - size) / 2f
        return Offset(
            x = candidate.x.coerceIn(min(minX, maxX), max(minX, maxX)),
            y = candidate.y.coerceIn(min(minY, maxY), max(minY, maxY))
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        zoom = newZoom
        offset = clampOffset(offset + panChange, newZoom)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .testTag("avatar_cropper")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Frame your picture",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Drag to move, pinch to zoom. It is cropped square.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(2.dp, ElectricCyan, RoundedCornerShape(16.dp))
                    .onSizeChanged { viewportSize = it.width }
                    .testTag("avatar_cropper_viewport")
            ) {
                Image(
                    bitmap = source.asImageBitmap(),
                    contentDescription = null,
                    // The picture is given exactly the size that fills the square, so it is drawn
                    // one to one and the crop is taken from the right pixels.
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .align(Alignment.Center)
                        // requiredSize, not size: the picture is larger than the square on purpose,
                        // and the ordinary size would be clamped down to the square's own size,
                        // which would stretch the picture instead of cropping it. The square's
                        // clip then hides the part that is outside the frame.
                        .requiredSize(
                            width = with(density) { coverWidth.toDp() },
                            height = with(density) { coverHeight.toDp() }
                        )
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(state = transformState)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onCrop(
                        ImageCropper.crop(
                            source = source,
                            viewportSize = viewportSize,
                            zoom = zoom,
                            offsetX = offset.x,
                            offsetY = offset.y
                        )
                    )
                },
                enabled = viewportSize > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("avatar_cropper_confirm")
            ) {
                Text(
                    text = "Use this picture",
                    color = DarkBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("avatar_cropper_cancel")
            ) {
                Text("Cancel", color = TextMuted)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** How far past the fitted size the picture may be zoomed. */
private const val MAX_ZOOM = 4f
