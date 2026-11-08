/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.ProfileMetrics
import com.example.data.model.WeightConversion
import com.example.ui.components.AvatarCropper
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ImageCropper
import com.example.util.ProfileMediaStore
import java.io.File

/**
 * The Athlete Profile card that Settings now hosts.
 *
 * The visual card and editing flow are the ones previously on the Profile tab. Profile data,
 * avatars and the editor still go through the same ViewModel methods and preferences.
 */
@Composable
fun AthleteProfileCard(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val avatarPath by viewModel.profileAvatarPath.collectAsStateWithLifecycle()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarActionsDialog by remember { mutableStateOf(false) }
    var avatarImportFailed by remember { mutableStateOf(false) }
    var pendingAvatarCrop by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val decoded = ImageCropper.decode(context, uri)
            if (decoded != null) pendingAvatarCrop = decoded else avatarImportFailed = true
        }
    )

    fun acceptCrop(bitmap: Bitmap) {
        val localPath = ProfileMediaStore.write(context, bitmap)
        pendingAvatarCrop = null
        if (localPath != null) viewModel.setProfileAvatar(localPath) else avatarImportFailed = true
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, ElectricCyan, CircleShape)
                        .clickable {
                            if (avatarPath.isBlank()) {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            } else {
                                showAvatarActionsDialog = true
                            }
                        }
                        .testTag("profile_avatar_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarPath.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = File(avatarPath),
                            contentDescription = "Profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userProfile.name.ifBlank { "Athlete" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${userProfile.fitnessLevel} • ${userProfile.age} yrs • " +
                            "${userProfile.heightCm.toInt()} cm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                IconButton(
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier.size(40.dp).testTag("edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileMetricBox(
                    label = "CURRENT WEIGHT",
                    value = WeightConversion.format(userProfile.currentWeightKg, weightUnit),
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ProfileMetricBox(
                    label = "TARGET WEIGHT",
                    value = WeightConversion.format(userProfile.targetWeightKg, weightUnit),
                    color = MintGreen,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ProfileMetricBox(
                    label = "BMI",
                    value = ProfileMetrics.bmiText(
                        userProfile.currentWeightKg,
                        userProfile.heightCm
                    ),
                    color = GoldenAmber,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val diff = userProfile.targetWeightKg - userProfile.currentWeightKg
            val diffText = WeightConversion.format(diff, weightUnit, withUnit = false)
            val deltaText = when {
                diff > 0 -> "+$diffText ${weightUnit.suffix} to target (Bulking / Strength Surplus)"
                diff < 0 -> "$diffText ${weightUnit.suffix} to target (Cutting / Lean Deficit)"
                else -> "Target reached! Maintenance phase."
            }
            Text(
                text = "Goal Strategy: $deltaText",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (diff >= 0) MintGreen else GoldenAmber
            )
        }
    }

    if (showAvatarActionsDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarActionsDialog = false },
            containerColor = DarkSurface,
            title = { Text("Profile picture", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You can pick a different picture, or take this one away. The copy in this app " +
                        "is deleted when you remove it; the original in your gallery is not touched.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAvatarActionsDialog = false
                        avatarPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Change picture", color = OnAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showAvatarActionsDialog = false
                            viewModel.clearProfileAvatar()
                        }
                    ) { Text("Remove", color = FireRed) }
                    TextButton(onClick = { showAvatarActionsDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            }
        )
    }

    pendingAvatarCrop?.let { bitmap ->
        AvatarCropper(
            source = bitmap,
            onCrop = { cropped -> acceptCrop(cropped) },
            onCancel = { pendingAvatarCrop = null }
        )
    }

    if (avatarImportFailed) {
        AlertDialog(
            onDismissRequest = { avatarImportFailed = false },
            containerColor = DarkSurface,
            title = { Text("Could not use that picture", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The file could not be copied into the app. Pick another one.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { avatarImportFailed = false }) {
                    Text("OK", color = ElectricCyan)
                }
            }
        )
    }

    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(userProfile.name) }
        var heightInput by remember { mutableStateOf(userProfile.heightCm.toInt().toString()) }
        var currentWeightInput by remember {
            mutableStateOf(
                WeightConversion.format(userProfile.currentWeightKg, weightUnit, withUnit = false)
            )
        }
        var targetWeightInput by remember {
            mutableStateOf(
                WeightConversion.format(userProfile.targetWeightKg, weightUnit, withUnit = false)
            )
        }
        var ageInput by remember { mutableStateOf(userProfile.age.toString()) }
        var levelInput by remember { mutableStateOf(userProfile.fitnessLevel) }
        var unitInput by remember { mutableStateOf(userProfile.preferredUnit) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = DarkSurface,
            title = { Text("Edit Athlete Profile", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { input ->
                            if (input.length <= MainViewModel.MAX_NAME_LENGTH) nameInput = input
                        },
                        label = { Text("Name (max ${MainViewModel.MAX_NAME_LENGTH} characters)") },
                        supportingText = {
                            Text(
                                text = "${nameInput.length}/${MainViewModel.MAX_NAME_LENGTH}",
                                color = TextMuted
                            )
                        },
                        colors = athleteFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text("Height (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = athleteFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { ageInput = it },
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = athleteFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currentWeightInput,
                            onValueChange = { currentWeightInput = it },
                            label = { Text("Current Wt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = athleteFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetWeightInput,
                            onValueChange = { targetWeightInput = it },
                            label = { Text("Target Wt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = athleteFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Fitness Level:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                            FilterChip(
                                selected = levelInput == level,
                                onClick = { levelInput = level },
                                label = { Text(level, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan,
                                    selectedLabelColor = OnAccent,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextWhite
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val height = heightInput.toDoubleOrNull() ?: userProfile.heightCm
                        val current = WeightConversion.parseToKg(currentWeightInput, weightUnit)
                            ?: userProfile.currentWeightKg
                        val target = WeightConversion.parseToKg(targetWeightInput, weightUnit)
                            ?: userProfile.targetWeightKg
                        val age = ageInput.toIntOrNull() ?: userProfile.age
                        viewModel.updateProfile(
                            userProfile.copy(
                                name = nameInput.trim().take(MainViewModel.MAX_NAME_LENGTH),
                                heightCm = height,
                                currentWeightKg = current,
                                targetWeightKg = target,
                                age = age,
                                fitnessLevel = levelInput,
                                preferredUnit = unitInput
                            )
                        )
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Save Changes", color = OnAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun ProfileMetricBox(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkSurfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun athleteFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted
)
