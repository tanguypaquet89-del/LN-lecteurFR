package com.nahrahviing.lecteurnovel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.nahrahviing.lecteurnovel.util.DownloadQueueManager
import com.nahrahviing.lecteurnovel.util.DownloadStatus
import com.nahrahviing.lecteurnovel.util.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tasks by DownloadQueueManager.tasks.collectAsState()
    val activeCount by DownloadQueueManager.activeCount.collectAsState()

    var taskToDeleteFiles by remember { mutableStateOf<DownloadTask?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // En-tête
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "File de téléchargement",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (activeCount > 0) "$activeCount actif(s)" else "Aucun téléchargement actif",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }

                // Barre d'actions globales
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeCount > 0) {
                        OutlinedButton(
                            onClick = { DownloadQueueManager.cancelAll(context) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tout annuler", fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (tasks.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELLED || it.status == DownloadStatus.FAILED }) {
                        TextButton(
                            onClick = { DownloadQueueManager.clearFinished() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Effacer terminés", fontSize = 12.sp)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Liste des tâches
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "La file d'attente est vide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Naviguez et lancez un téléchargement en EPUB, PDF, ODT ou TXT.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            DownloadTaskItemCard(
                                task = task,
                                onCancel = { DownloadQueueManager.cancelTask(context, task.id) },
                                onRelaunch = { DownloadQueueManager.relaunchTask(context, task.id) },
                                onDeleteFile = { taskToDeleteFiles = task },
                                onRemoveFromQueue = { DownloadQueueManager.removeFromQueue(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation de suppression des fichiers téléchargés
    taskToDeleteFiles?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDeleteFiles = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer les fichiers ?") },
            text = {
                Text("Voulez-vous supprimer les fichiers générés pour « ${task.novelTitle} » (${task.format}) de la mémoire de l'appareil ?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        DownloadQueueManager.deleteDownloadedFilesForTask(context, task.id)
                        taskToDeleteFiles = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDeleteFiles = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun DownloadTaskItemCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRelaunch: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemoveFromQueue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Couverture
                if (task.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = task.coverUrl,
                        contentDescription = task.novelTitle,
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Titre & badges
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.novelTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = task.format,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = if (task.exportMode == "SEPARATE_CHAPTERS") "Fichiers séparés" else "Document unique",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (task.status) {
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            DownloadStatus.COMPLETED -> Color(0xFF2E7D32)
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline
                            DownloadStatus.QUEUED -> MaterialTheme.colorScheme.secondary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Boutons d'action selon statut
                when (task.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRelaunch) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Relancer le téléchargement",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onCancel) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = "Annuler le téléchargement",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRelaunch) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Retélécharger / Relancer",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onDeleteFile) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Supprimer les fichiers",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = onRemoveFromQueue) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Retirer de la file",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    DownloadStatus.CANCELLED, DownloadStatus.FAILED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRelaunch) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Relancer le téléchargement",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onRemoveFromQueue) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Barre de progression
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { if (task.status == DownloadStatus.QUEUED) 0f else task.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(task.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
