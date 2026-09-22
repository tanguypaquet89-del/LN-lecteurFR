package com.nahrahviing.lecteurnovel.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSection(
    driveConnectedEmail: String?,
    lastDriveBackupTimestamp: Long,
    lastDriveBackupStatus: String?,
    isAutoDriveBackupEnabled: Boolean,
    isAutoEpubSyncEnabled: Boolean,
    isAutoDownloadOnRestoreEnabled: Boolean = true,
    isProcessingBackup: Boolean,
    backupStatusMessage: String?,
    onShowDriveConfigDialog: () -> Unit,
    onToggleAutoDriveBackup: (Boolean) -> Unit,
    onToggleAutoEpubSync: (Boolean) -> Unit,
    onToggleAutoDownloadOnRestore: (Boolean) -> Unit = {},
    onRestoreDriveClick: () -> Unit,
    onBackupDriveClick: () -> Unit,
    onSyncAllEpubsClick: () -> Unit,
    // P2P
    onP2PReceiveClick: () -> Unit,
    onP2PSendClick: () -> Unit,
    // Local JSON
    onImportJsonClick: () -> Unit,
    onShareBackupClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Synchronisation & Sauvegarde",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Google Drive
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Drive Cloud", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        }
                        IconButton(onClick = onShowDriveConfigDialog) {
                            Icon(Icons.Default.Info, contentDescription = "Aide configuration Cloud", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (!driveConnectedEmail.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compte connecté : $driveConnectedEmail", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (lastDriveBackupTimestamp > 0L) {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        Text(
                            "Dernière sauvegarde : ${sdf.format(Date(lastDriveBackupTimestamp))} (${lastDriveBackupStatus ?: "Réussie"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // 1. Sauvegarde automatique sur le Drive
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Sauvegarde automatique sur le Drive", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text("Sauvegarde votre bibliothèque toutes les 12h et après chaque téléchargement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoDriveBackupEnabled,
                            onCheckedChange = onToggleAutoDriveBackup
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Enregistrer automatiquement les EPUB
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Enregistrer les EPUB sur Drive", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text("Téléverse automatiquement chaque roman EPUB généré ou mis à jour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoEpubSyncEnabled,
                            onCheckedChange = onToggleAutoEpubSync
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Télécharger automatiquement après restauration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto-téléchargement après restauration", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text("Télécharge en arrière-plan les chapitres manquants lors de l'import d'une sauvegarde.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoDownloadOnRestoreEnabled,
                            onCheckedChange = onToggleAutoDownloadOnRestore
                        )
                    }

                    if (isProcessingBackup) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                backupStatusMessage ?: "Traitement en cours...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Boutons d'actions manuelles
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRestoreDriveClick,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessingBackup
                        ) {
                            Text("Restaurer", fontSize = 12.sp)
                        }
                        Button(
                            onClick = onBackupDriveClick,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessingBackup
                        ) {
                            Text("Sauvegarder", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onSyncAllEpubsClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessingBackup
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synchroniser tous les EPUB maintenant", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Transfert Local (P2P)
            P2PSyncSection(
                onReceiveClick = onP2PReceiveClick,
                onSendClick = onP2PSendClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 3: Export Manuel JSON
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Manuel (JSON)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Exportez ou importez un fichier de sauvegarde classique.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = onImportJsonClick) {
                            Text("Importer")
                        }
                        Row {
                            OutlinedButton(onClick = onShareBackupClick) {
                                Text("Partager")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = onExportBackupClick) {
                                Text("Exporter")
                            }
                        }
                    }
                }
            }
        }
    }
}
