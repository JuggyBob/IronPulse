/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AccentColors
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A modern, dependency-free custom accent picker.
 *
 * The outer ring selects hue; the inner square selects saturation and brightness. The same colour
 * is kept in sync with the HEX and R/G/B fields, and the dialog only hands a colour back when the
 * athlete presses Apply. Canceling therefore never changes the saved accent.
 */
@Composable
fun AccentColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) { hsvOf(initialColor) }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var hexText by remember(initialColor) { mutableStateOf(AccentColors.toHex(initialColor)) }
    var redText by remember(initialColor) { mutableStateOf(channelText(initialColor.red)) }
    var greenText by remember(initialColor) { mutableStateOf(channelText(initialColor.green)) }
    var blueText by remember(initialColor) { mutableStateOf(channelText(initialColor.blue)) }

    fun setFromHsv(newHue: Float, newSaturation: Float, newBrightness: Float) {
        val color = colorFromHsv(newHue, newSaturation, newBrightness)
        hue = newHue
        saturation = newSaturation
        brightness = newBrightness
        selectedColor = color
        hexText = AccentColors.toHex(color)
        redText = channelText(color.red)
        greenText = channelText(color.green)
        blueText = channelText(color.blue)
    }

    fun setFromColor(color: Color, updateHex: Boolean, updateRgb: Boolean) {
        val hsv = hsvOf(color)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        selectedColor = color
        if (updateHex) hexText = AccentColors.toHex(color)
        if (updateRgb) {
            redText = channelText(color.red)
            greenText = channelText(color.green)
            blueText = channelText(color.blue)
        }
    }

    fun applyRgbIfValid() {
        val red = redText.toIntOrNull() ?: return
        val green = greenText.toIntOrNull() ?: return
        val blue = blueText.toIntOrNull() ?: return
        if (red !in 0..255 || green !in 0..255 || blue !in 0..255) return
        setFromColor(
            Color(AndroidColor.rgb(red, green, blue)),
            updateHex = true,
            updateRgb = false
        )
    }

    val hexValid = AccentColors.parseHex(hexText) != null
    val rgbValid = listOf(redText, greenText, blueText).all { text ->
        text.toIntOrNull()?.let { it in 0..255 } == true
    }
    val canApply = hexValid && rgbValid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .testTag("accent_color_picker_dialog"),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Accent Color",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextWhite
                            )
                            Text(
                                text = "Pick a hue, then tune saturation and brightness.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("accent_picker_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(2.dp, TextWhite.copy(alpha = 0.35f), CircleShape)
                                .testTag("accent_picker_preview")
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = AccentColors.toHex(selectedColor),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextWhite
                            )
                            Text(
                                text = "Live preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        HueSaturationWheel(
                            hue = hue,
                            saturation = saturation,
                            value = brightness,
                            onHueChange = { newHue ->
                                setFromHsv(newHue, saturation, brightness)
                            },
                            onSaturationValueChange = { newSaturation, newValue ->
                                setFromHsv(hue, newSaturation, newValue)
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .aspectRatio(1f)
                                .testTag("accent_color_wheel")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            val hasHash = raw.startsWith("#")
                            val digits = raw
                                .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                                .take(6)
                            val next = if (hasHash) "#$digits" else digits
                            hexText = next
                            AccentColors.parseHex(next)?.let { color ->
                                setFromColor(color, updateHex = false, updateRgb = true)
                            }
                        },
                        label = { Text("HEX") },
                        placeholder = { Text("#3985E8") },
                        singleLine = true,
                        isError = hexText.isNotEmpty() && !hexValid,
                        supportingText = if (hexText.isNotEmpty() && !hexValid) {
                            { Text("Enter a six-digit HEX color, for example #3985E8.") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        colors = pickerFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accent_hex_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChannelField(
                            label = "R",
                            value = redText,
                            onValueChange = {
                                redText = it.filter(Char::isDigit).take(3)
                                applyRgbIfValid()
                            },
                            tag = "accent_rgb_r",
                            modifier = Modifier.weight(1f)
                        )
                        ChannelField(
                            label = "G",
                            value = greenText,
                            onValueChange = {
                                greenText = it.filter(Char::isDigit).take(3)
                                applyRgbIfValid()
                            },
                            tag = "accent_rgb_g",
                            modifier = Modifier.weight(1f)
                        )
                        ChannelField(
                            label = "B",
                            value = blueText,
                            onValueChange = {
                                blueText = it.filter(Char::isDigit).take(3)
                                applyRgbIfValid()
                            },
                            tag = "accent_rgb_b",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!rgbValid) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "R, G and B must each be between 0 and 255.",
                            style = MaterialTheme.typography.labelSmall,
                            color = FireRed
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("accent_picker_cancel")
                        ) {
                            Text("Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalColor = AccentColors.parseHex(hexText) ?: selectedColor
                                onApply(finalColor)
                            },
                            enabled = canApply,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = OnAccent,
                                disabledContainerColor = DarkSurfaceVariant,
                                disabledContentColor = TextDim
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("accent_picker_apply")
                        ) {
                            Text("Apply", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val invalid = value.toIntOrNull()?.let { it in 0..255 } != true
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = pickerFieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier.testTag(tag)
    )
}

@Composable
private fun pickerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted,
    cursorColor = ElectricCyan,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedContainerColor = DarkBg,
    unfocusedContainerColor = DarkBg
)

/**
 * Hue ring plus a saturation/brightness square.
 *
 * Both regions use direct pointer coordinates, so the drawn indicator and the selected colour are
 * always the same point under the finger. The ring is drawn with a sweep gradient; hue 0 starts at
 * the three o'clock position and increases clockwise, matching the angle used by the touch handler.
 */
@Composable
private fun HueSaturationWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnHueChange by rememberUpdatedState(onHueChange)
    val currentOnSaturationValueChange by rememberUpdatedState(onSaturationValueChange)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Hue, saturation and brightness picker" }
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    val diameter = minOf(size.width, size.height).toFloat()
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val ringWidth = diameter * 0.14f
                    val outerRadius = diameter * 0.5f
                    val innerRadius = outerRadius - ringWidth
                    val squareSide = diameter * 0.50f
                    val squareLeft = centerX - squareSide / 2f
                    val squareTop = centerY - squareSide / 2f
                    val squareRight = squareLeft + squareSide
                    val squareBottom = squareTop + squareSide

                    if (position.x in squareLeft..squareRight &&
                        position.y in squareTop..squareBottom
                    ) {
                        val newSaturation =
                            ((position.x - squareLeft) / squareSide).coerceIn(0f, 1f)
                        val newValue =
                            (1f - (position.y - squareTop) / squareSide).coerceIn(0f, 1f)
                        currentOnSaturationValueChange(newSaturation, newValue)
                        return
                    }

                    val distance = hypot(position.x - centerX, position.y - centerY)
                    if (distance >= innerRadius - diameter * 0.04f &&
                        distance <= outerRadius + diameter * 0.04f
                    ) {
                        var degrees = Math.toDegrees(
                            atan2(
                                (position.y - centerY).toDouble(),
                                (position.x - centerX).toDouble()
                            )
                        ).toFloat()
                        if (degrees < 0f) degrees += 360f
                        currentOnHueChange(degrees % 360f)
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position)
                    down.consume()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                update(change.position)
                                change.consume()
                            }
                        }
                        event = awaitPointerEvent()
                    }
                }
            }
    ) {
        val diameter = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringWidth = diameter * 0.14f
        val ringRadius = diameter * 0.5f - ringWidth / 2f
        val squareSide = diameter * 0.50f
        val squareTopLeft = Offset(
            center.x - squareSide / 2f,
            center.y - squareSide / 2f
        )
        val squareSize = Size(squareSide, squareSide)

        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color.Red,
                1f / 6f to Color.Yellow,
                2f / 6f to Color.Green,
                0.5f to Color.Cyan,
                4f / 6f to Color.Blue,
                5f / 6f to Color.Magenta,
                1f to Color.Red
            ),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringWidth)
        )

        // Cover the one-pixel seam where a sweep shader wraps at three o'clock. The hue there is
        // pure red, so a short solid arc is visually indistinguishable and keeps the ring clean.
        drawArc(
            color = Color.Red,
            startAngle = -2f,
            sweepAngle = 4f,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = ringWidth)
        )

        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.White, colorFromHsv(hue, 1f, 1f))
            ),
            topLeft = squareTopLeft,
            size = squareSize
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black)
            ),
            topLeft = squareTopLeft,
            size = squareSize
        )
        drawRect(
            color = TextWhite.copy(alpha = 0.28f),
            topLeft = squareTopLeft,
            size = squareSize,
            style = Stroke(width = 1.5f)
        )

        val angle = Math.toRadians(hue.toDouble())
        val hueIndicator = Offset(
            center.x + cos(angle).toFloat() * ringRadius,
            center.y + sin(angle).toFloat() * ringRadius
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = diameter * 0.035f,
            center = hueIndicator
        )
        drawCircle(
            color = Color.White,
            radius = diameter * 0.023f,
            center = hueIndicator
        )

        val svIndicator = Offset(
            squareTopLeft.x + saturation * squareSide,
            squareTopLeft.y + (1f - value) * squareSide
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.6f),
            radius = diameter * 0.034f,
            center = svIndicator,
            style = Stroke(width = diameter * 0.011f)
        )
        drawCircle(
            color = Color.White,
            radius = diameter * 0.022f,
            center = svIndicator
        )
    }
}

private fun colorFromHsv(hue: Float, saturation: Float, value: Float): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))

private fun hsvOf(color: Color): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun channelText(channel: Float): String =
    (channel * 255f).roundToInt().coerceIn(0, 255).toString()
