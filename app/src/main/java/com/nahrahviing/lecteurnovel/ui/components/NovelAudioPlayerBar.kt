package com.nahrahviing.lecteurnovel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahrahviing.lecteurnovel.tts.TtsPlayState
import com.nahrahviing.lecteurnovel.tts.VoiceOption

// Teinte Ruby emblématique de Novel France
val RubyAccentColor = Color(0xFFE11D48)
val RubyAccentLight = Color(0xFFFFF1F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelAudioPlayerBar(
    playState: TtsPlayState,
    currentVoice: VoiceOption,
    availableVoices: List<VoiceOption>,
    speed: Float,
    progressMs: Long,
    durationMs: Long,
    statusMessage: String,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVoiceSelect: (VoiceOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val isPlaying = playState == TtsPlayState.PLAYING
    val isLoading = playState == TtsPlayState.LOADING || playState == TtsPlayState.PREPARING

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Ligne principale : Play/Pause, Informations de voix et état, Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bouton principal circulaire rouge Ruby
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(RubyAccentColor)
                        .clickable(enabled = !isLoading) { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (isPlaying) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Mettre en pause",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lire le chapitre",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Titre et statut
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (playState) {
                            TtsPlayState.PREPARING, TtsPlayState.LOADING -> "PRÉPARATION"
                            TtsPlayState.PLAYING -> "EN LECTURE"
                            TtsPlayState.PAUSED -> "EN PAUSE"
                            TtsPlayState.ERROR -> "ÉCHEC"
                            else -> "LECTURE VOCALE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) RubyAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = currentVoice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Le texte se surligne au fil de la voix",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }

                // Bouton Choix de la Voix
                Surface(
                    onClick = { showVoiceSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = "Voix",
                            modifier = Modifier.size(16.dp),
                            tint = RubyAccentColor
                        )
                        Text(
                            text = "Voix",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Bouton Vitesse de lecture
                Box {
                    Surface(
                        onClick = { showSpeedMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "${speed}×",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${s}×" + if (s == 1.0f) " (normal)" else "",
                                        fontWeight = if (s == speed) FontWeight.Bold else FontWeight.Normal,
                                        color = if (s == speed) RubyAccentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSpeedChange(s)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }

                // Bouton Arrêter / Fermer la barre
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer la lecture vocale",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Barre de progression (Slider) et commandes de saut
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipBackward,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "-10s",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val safeDuration = durationMs.coerceAtLeast(1L)
                val safeProgress = progressMs.coerceIn(0L, safeDuration)

                Slider(
                    value = safeProgress.toFloat(),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = RubyAccentColor,
                        activeTrackColor = RubyAccentColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                IconButton(
                    onClick = onSkipForward,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "+10s",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Timers : Temps écoulé et Durée totale
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(progressMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (durationMs > 0) formatMs(durationMs) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Modal BottomSheet pour sélectionner la voix ultra-réaliste
    if (showVoiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVoiceSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Choisir la voix de narration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voix IA Novel France & Synthèses Neuronales HD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1 : Voix Novel France
                val nfVoices = availableVoices.filter { it.isNovelFranceVoice }
                if (nfVoices.isNotEmpty()) {
                    Text(
                        text = "VOIX OFFICIELLES NOVEL FRANCE (IA)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RubyAccentColor,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    nfVoices.forEach { voice ->
                        val isSelected = voice.id == currentVoice.id
                        Surface(
                            onClick = {
                                onVoiceSelect(voice)
                                showVoiceSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) RubyAccentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, RubyAccentColor) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isSelected) RubyAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) RubyAccentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = voice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sélectionnée",
                                        tint = RubyAccentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Section 2 : Voix Système / Google Speech Services
                val localVoices = availableVoices.filter { !it.isNovelFranceVoice }
                if (localVoices.isNotEmpty()) {
                    Text(
                        text = "VOIX NEURONALES GOOGLE & SYSTÈME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    localVoices.take(6).forEach { voice ->
                        val isSelected = voice.id == currentVoice.id
                        Surface(
                            onClick = {
                                onVoiceSelect(voice)
                                showVoiceSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = voice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sélectionnée",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
