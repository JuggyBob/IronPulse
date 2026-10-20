package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay

/**
 * Shows that the workout in progress is being written to disk.
 *
 * The workout used to live only in memory, so closing the app lost it, and the athlete had no way
 * of knowing that. This says so, and says it *when* it happened: the line brightens on every save
 * and then settles back down, so a save is noticeable without being noisy.
 */
@Composable
fun AutoSaveIndicator(
    lastAutoSaveAt: Long?,
    modifier: Modifier = Modifier
) {
    if (lastAutoSaveAt == null) return

    // A clock of its own, so the "x seconds ago" text stays honest without the whole screen
    // recomposing once a second.
    val now by produceState(initialValue = System.currentTimeMillis(), lastAutoSaveAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // Brightens on every save, then settles back down.
    var flash by remember(lastAutoSaveAt) { mutableStateOf(false) }
    LaunchedEffect(lastAutoSaveAt) {
        flash = true
        delay(900L)
        flash = false
    }
    val emphasis by animateFloatAsState(
        targetValue = if (flash) 1f else 0.45f,
        animationSpec = tween(durationMillis = 450),
        label = "autosave_emphasis"
    )

    val secondsAgo = ((now - lastAutoSaveAt) / 1000L).coerceAtLeast(0L)
    val label = when {
        secondsAgo < 3L -> "Saved just now"
        secondsAgo < 60L -> "Saved ${secondsAgo}s ago"
        else -> "Saved ${secondsAgo / 60L}m ago"
    }

    Row(
        modifier = modifier
            .background(
                color = MintGreen.copy(alpha = 0.10f),
                shape = RoundedCornerShape(6.dp)
            )
            .border(1.dp, MintGreen.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = MintGreen.copy(alpha = emphasis),
            modifier = Modifier
                .size(12.dp)
                .scale(1f + emphasis * 0.15f)
        )
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.9f)) togetherWith
                    (fadeOut(tween(220)) + scaleOut(targetScale = 0.9f))
            },
            label = "autosave_label"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (emphasis > 0.7f) MintGreen else TextMuted
            )
        }
    }
}
