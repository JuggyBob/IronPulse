package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.OnAccent
import com.example.ui.theme.SuccessFlash
import kotlinx.coroutines.delay

/** The green the cloud flashes when a save just happened: deliberately lighter than MintGreen. */
private val AutoSaveFlashGreen: Color get() = SuccessFlash

/** The quiet green of a cloud that is known to be up to date. */
private val AutoSaveSavedGreen: Color get() = MintGreen.copy(alpha = 0.8f)

/**
 * The auto save state of a workout in progress, as a single small cloud.
 *
 * The old badge said "Saved 12s ago" in words and took a whole line under the workout title. The
 * workout already has a timer in a badge of its own; the save state belongs there, as one icon,
 * because that is where the athlete is already looking to see that the session is running.
 *
 * The cloud is struck through with a check while the save is fresh ([lastAutoSaveAt] changing), and
 * a small check pops out of it at the same time. The icon then settles back to a quiet green so it
 * says "saved" without pulling the eye away from the set being logged.
 */
@Composable
fun AutoSaveCloudIcon(
    lastAutoSaveAt: Long?,
    modifier: Modifier = Modifier
) {
    // Every new save timestamp replays the flourish. It stays up for a moment, then fades.
    var flashing by remember(lastAutoSaveAt) { mutableStateOf(false) }
    LaunchedEffect(lastAutoSaveAt) {
        if (lastAutoSaveAt == null) return@LaunchedEffect
        flashing = true
        delay(1_250L)
        flashing = false
    }

    val tint = if (flashing) AutoSaveFlashGreen else if (lastAutoSaveAt == null) {
        TextMuted.copy(alpha = 0.65f)
    } else {
        AutoSaveSavedGreen
    }
    val scale by animateFloatAsState(
        targetValue = if (flashing) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "autosave_cloud_scale"
    )

    Box(
        modifier = modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // A soft halo that only exists during the save, so the pop is visible against the timer
        // badge without the icon needing a filled background at rest.
        Box(
            modifier = Modifier
                .size(if (flashing) 18.dp else 0.dp)
                .background(AutoSaveFlashGreen.copy(alpha = 0.16f), CircleShape)
        )
        Icon(
            imageVector = if (lastAutoSaveAt == null) {
                Icons.Outlined.CloudQueue
            } else {
                Icons.Filled.CloudDone
            },
            contentDescription = when {
                lastAutoSaveAt == null -> "Workout not saved yet"
                flashing -> "Workout saved"
                else -> "Workout saved automatically"
            },
            tint = tint,
            modifier = Modifier
                .size(15.dp)
                .scale(scale)
        )
        AnimatedVisibility(
            visible = flashing,
            enter = scaleIn(
                initialScale = 0.2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(tween(120)),
            exit = scaleOut(targetScale = 0.55f, animationSpec = tween(300)) +
                fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-2).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(AutoSaveFlashGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = OnAccent,
                    modifier = Modifier.size(6.dp)
                )
            }
        }
    }
}

/**
 * The live workout timer with the auto save cloud on its left.
 *
 * One badge for both workout screens, so the quick workout and the routine workout show the state
 * the same way, and so the cloud physically sits inside the timer box rather than under the title.
 */
@Composable
fun WorkoutTimerBadge(
    timerText: String,
    lastAutoSaveAt: Long?,
    modifier: Modifier = Modifier,
    timerColor: Color = MintGreen
) {
    Row(
        modifier = modifier
            .background(DarkBg.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoSaveCloudIcon(lastAutoSaveAt = lastAutoSaveAt)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = timerText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = timerColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
