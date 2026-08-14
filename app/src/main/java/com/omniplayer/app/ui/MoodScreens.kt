package com.omniplayer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniplayer.app.model.BuiltInMoods
import com.omniplayer.app.model.MoodDefinition
import com.omniplayer.app.model.MoodRecommendation
import com.omniplayer.app.model.OmniMedia

fun moodIcon(mood: MoodDefinition): ImageVector = when (mood.id) {
    BuiltInMoods.CHILL -> Icons.Rounded.Headphones
    BuiltInMoods.WORKOUT -> Icons.Rounded.FitnessCenter
    BuiltInMoods.FOCUS -> Icons.Rounded.SportsEsports
    BuiltInMoods.PARTY -> Icons.Rounded.LocalFireDepartment
    BuiltInMoods.ROMANCE -> Icons.Rounded.Favorite
    BuiltInMoods.SLEEP -> Icons.Rounded.Bedtime
    else -> Icons.Rounded.AutoAwesome
}

@Composable
fun MoodHubScreen(
    moods: List<MoodDefinition>,
    recommendationCounts: Map<String, Int>,
    contentPadding: PaddingValues,
    onOpenMood: (MoodDefinition) -> Unit,
    onCreateMood: (String, String) -> Unit,
    onDeleteMood: (MoodDefinition) -> Unit,
    onOpenAcoustic: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<MoodDefinition?>(null) }
    if (showCreate) {
        CreateMoodDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, description ->
                onCreateMood(name, description)
                showCreate = false
            },
        )
    }
    deleteCandidate?.let { mood ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${mood.name}?") },
            text = { Text("The custom mood and its song assignments will be removed. Your audio files are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMood(mood)
                    deleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
        )
    }

    OmniBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OmniHeader(
                    title = "Mood mixes",
                    subtitle = "Smart suggestions from the music already on your phone",
                    action = {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Rounded.Add, "Create custom mood", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
            item {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                ) {
                    Row(
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Your library, remembered by feeling", fontWeight = FontWeight.Bold)
                            Text(
                                "A song can appear in several moods. Add your own tags anytime to teach Omni what feels right to you.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAcoustic),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                RoundedCornerShape(14.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Acoustic analysis (Beta)", fontWeight = FontWeight.Bold)
                            Text(
                                "Optional on-device sound analysis. Start it only when you want deeper mood matching.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            items(moods, key = MoodDefinition::id) { mood ->
                MoodCollectionCard(
                    mood = mood,
                    count = recommendationCounts[mood.id] ?: 0,
                    onClick = { onOpenMood(mood) },
                    onDelete = if (mood.isCustom) ({ deleteCandidate = mood }) else null,
                )
            }
            item {
                Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create a custom mood")
                }
            }
        }
    }
}

@Composable
private fun MoodCollectionCard(
    mood: MoodDefinition,
    count: Int,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val color = Color(mood.colorArgb)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(moodIcon(mood), null, tint = color) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(mood.name, fontWeight = FontWeight.Bold)
                Text(
                    mood.description.ifBlank { "A personal mood created by you." },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("$count matching songs", color = color, style = MaterialTheme.typography.labelSmall)
            }
            onDelete?.let { delete ->
                IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, "Delete ${mood.name}") }
            }
        }
    }
}

@Composable
fun MoodDetailScreen(
    mood: MoodDefinition,
    recommendations: List<MoodRecommendation>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayMix: (List<OmniMedia>) -> Unit,
    onPlay: (OmniMedia, List<OmniMedia>) -> Unit,
    onToggleMood: (OmniMedia, String) -> Unit,
) {
    val color = Color(mood.colorArgb)
    val queue = recommendations.map(MoodRecommendation::media)
    OmniBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 10.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text("Mood mix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
                ) {
                    Column(
                        Modifier.background(
                            Brush.linearGradient(listOf(color.copy(alpha = 0.3f), MaterialTheme.colorScheme.surfaceVariant)),
                        ).padding(20.dp),
                    ) {
                        Box(
                            Modifier.size(58.dp).background(color.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(moodIcon(mood), null, tint = color, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.height(14.dp))
                        Text(mood.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(mood.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onPlayMix(queue) },
                            enabled = queue.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Play mood mix (${queue.size})")
                        }
                    }
                }
            }
            item {
                Text("Recommended from your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Suggestions use track metadata, optional acoustic results and your own assignments. Tap the sparkle to teach Omni.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (recommendations.isEmpty()) {
                item {
                    EmptyState(
                        "No confident matches yet",
                        "Add this mood to songs from the audio player, or expand the custom mood description with artists, genres and feelings.",
                    )
                }
            } else {
                items(recommendations, key = { it.media.uri.toString() }) { recommendation ->
                    MoodRecommendationRow(
                        recommendation = recommendation,
                        color = color,
                        onPlay = { onPlay(recommendation.media, queue) },
                        onToggle = { onToggleMood(recommendation.media, mood.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodRecommendationRow(
    recommendation: MoodRecommendation,
    color: Color,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArtwork(recommendation.media, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(recommendation.media.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                recommendation.media.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                recommendation.reason,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (recommendation.manuallyAssigned) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
                if (recommendation.manuallyAssigned) "Remove your mood assignment" else "Confirm this suggestion",
                tint = color,
            )
        }
    }
}

@Composable
fun MoodAssignmentDialog(
    song: OmniMedia,
    moods: List<MoodDefinition>,
    manuallySelected: Set<String>,
    suggested: Set<String>,
    onToggle: (MoodDefinition) -> Unit,
    onManageMoods: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Moods for ${song.title}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Choose as many as you want. Suggested moods are detected from the song information.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                moods.forEach { mood ->
                    val selected = mood.id in manuallySelected
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onToggle(mood) },
                        color = if (selected) Color(mood.colorArgb).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(moodIcon(mood), null, tint = Color(mood.colorArgb), modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(mood.name, fontWeight = FontWeight.SemiBold)
                                if (!selected && mood.id in suggested) {
                                    Text("Suggested by Omni", color = Color(mood.colorArgb), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Checkbox(checked = selected, onCheckedChange = { onToggle(mood) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onManageMoods()
            }) { Text("Manage moods") }
        },
    )
}

@Composable
private fun CreateMoodDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a custom mood") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text("Mood name") },
                    placeholder = { Text("Rainy midnight") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(280) },
                    label = { Text("Describe the feeling") },
                    placeholder = { Text("Soft piano, rainy-night songs, slow indie music and Lana Del Rey") },
                    minLines = 3,
                    maxLines = 5,
                )
                Text(
                    "Mention genres, artists, words or situations. Omni uses the description to find matching songs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description) },
                enabled = name.isNotBlank() && description.trim().length >= 4,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MoodPill(
    mood: MoodDefinition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.background(Color(mood.colorArgb).copy(alpha = 0.14f), RoundedCornerShape(50))
    Row(
        modifier = if (onClick == null) base else base.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            moodIcon(mood),
            null,
            tint = Color(mood.colorArgb),
            modifier = Modifier.padding(start = 9.dp).size(15.dp),
        )
        Text(
            mood.name,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            color = Color(mood.colorArgb),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
