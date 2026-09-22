package com.nahrahviing.lecteurnovel.ui.screens.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.util.DownloadQueueManager
import com.nahrahviing.lecteurnovel.util.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDialog(
    novel: NovelInfo,
    onDismiss: () -> Unit,
    onDownload: (format: String, exportMode: String, startChap: Int, endChap: Int) -> Unit,
    onRelaunch: ((format: String, exportMode: String, startChap: Int, endChap: Int) -> Unit)? = null,
    onAddToLibraryWithoutDownload: (() -> Unit)? = null
) {
    val totalChapters = novel.chapters.size

    val allTasks by DownloadQueueManager.tasks.collectAsState()
    val activeTaskForThisNovel = remember(allTasks, novel.originalUrl) {
        allTasks.find {
            it.novelUrl == novel.originalUrl &&
            (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
        }
    }
    val otherActiveTasksCount = remember(allTasks, novel.originalUrl) {
        allTasks.count {
            it.novelUrl != novel.originalUrl &&
            (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
        }
    }

    // Sélection de l'étendue : "ALL" (tout) ou "BATCH" (par lot)
    var scopeMode by remember { mutableStateOf("ALL") }

    // Organisation des fichiers : "SINGLE_EPUB" (un seul fichier) ou "SEPARATE_CHAPTERS" (un par chapitre)
    var exportMode by remember { mutableStateOf("SINGLE_EPUB") }

    // Format : EPUB ou TXT
    var format by remember { mutableStateOf("EPUB") }

    var startChapStr by remember { mutableStateOf("1") }
    var endChapStr by remember { mutableStateOf(totalChapters.toString()) }

    val startChap = if (scopeMode == "ALL") 1 else (startChapStr.toIntOrNull() ?: 1).coerceIn(1, maxOf(1, totalChapters))
    val endChap = if (scopeMode == "ALL") totalChapters else (endChapStr.toIntOrNull() ?: totalChapters).coerceIn(startChap, maxOf(1, totalChapters))
    val selectedCount = maxOf(0, endChap - startChap + 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Options de téléchargement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info sur le roman
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$totalChapters chapitres détectés • ${novel.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Avertissement si un téléchargement est déjà actif pour ce roman ou d'autres
                if (activeTaskForThisNovel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Téléchargement déjà en cours pour ce roman (${(activeTaskForThisNovel.progress * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Vous pouvez relancer ce roman depuis le début ou ajouter un autre lot à la file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                } else if (otherActiveTasksCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$otherActiveTasksCount autre(s) téléchargement(s) en cours. Ce roman sera ajouté à la file d'attente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // 1. Choix de l'étendue : Tout ou Par lot
                Text(
                    text = "1. Chapitres à télécharger",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Option Tout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { scopeMode = "ALL" }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scopeMode == "ALL",
                            onClick = { scopeMode = "ALL" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Tous les chapitres", fontWeight = FontWeight.Medium)
                            Text(
                                "Télécharger l'intégralité ($totalChapters chapitres)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option Par lot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { scopeMode = "BATCH" }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scopeMode == "BATCH",
                            onClick = { scopeMode = "BATCH" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Par lot de chapitres", fontWeight = FontWeight.Medium)
                            Text(
                                "Définir une plage de chapitres personnalisée",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Champs de plage personnalisée si "Par lot"
                    if (scopeMode == "BATCH") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = startChapStr,
                                onValueChange = { startChapStr = it.filter { char -> char.isDigit() } },
                                label = { Text("Du chap.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endChapStr,
                                onValueChange = { endChapStr = it.filter { char -> char.isDigit() } },
                                label = { Text("Au chap.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. Organisation des fichiers : Un seul fichier complet vs Un fichier par chapitre
                Text(
                    text = "2. Organisation des fichiers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Option 1 : Un seul fichier complet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { exportMode = "SINGLE_EPUB" }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = exportMode == "SINGLE_EPUB",
                            onClick = { exportMode = "SINGLE_EPUB" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Un seul fichier complet ($format)", fontWeight = FontWeight.Medium)
                            Text(
                                "Tous les chapitres regroupés dans un unique document $format.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2 : Un fichier par chapitre
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { exportMode = "SEPARATE_CHAPTERS" }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = exportMode == "SEPARATE_CHAPTERS",
                            onClick = { exportMode = "SEPARATE_CHAPTERS" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Un fichier par chapitre ($format)", fontWeight = FontWeight.Medium)
                            Text(
                                "Chaque chapitre génère son propre fichier dans le dossier dédié du roman.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3. Choix du format (EPUB / PDF / ODT / TXT)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Format d'exportation :",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("EPUB", "PDF", "ODT", "TXT").forEach { fmt ->
                            FilterChip(
                                selected = format == fmt,
                                onClick = { format = fmt },
                                label = { Text(fmt, fontWeight = if (format == fmt) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = when (format) {
                            "EPUB" -> "• EPUB : Format liseuse standard (recommandé)"
                            "PDF" -> "• PDF : Document paginé avec mise en page fixe A4"
                            "ODT" -> "• ODT : Document texte ouvert modifiable (LibreOffice, Word)"
                            "TXT" -> "• TXT : Fichier texte brut universel et léger"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeTaskForThisNovel != null && onRelaunch != null) {
                    OutlinedButton(
                        onClick = {
                            onRelaunch(format, exportMode, startChap, endChap)
                        },
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Relancer")
                    }
                }
                Button(
                    onClick = {
                        onDownload(format, exportMode, startChap, endChap)
                    },
                    enabled = selectedCount > 0
                ) {
                    Text(
                        if (activeTaskForThisNovel != null) "Ajouter à la file ($selectedCount)"
                        else if (otherActiveTasksCount > 0) "Ajouter à la file ($selectedCount)"
                        else "Télécharger ($selectedCount)"
                    )
                }
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onAddToLibraryWithoutDownload != null) {
                    TextButton(
                        onClick = {
                            onAddToLibraryWithoutDownload()
                            onDismiss()
                        }
                    ) {
                        Text("Ajouter sans DL")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Annuler")
                }
            }
        }
    )
}
