package com.nahrahviing.lecteurnovel.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nahrahviing.lecteurnovel.util.StorageSummary

@Composable
fun StorageSettingsCard(
    storageSummary: StorageSummary?,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stockage Local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            storageSummary?.let { summary ->
                Text("Romans enregistrés : ${summary.downloadedNovelsCount}")
                Text("Taille des fichiers EPUB : ${String.format("%.2f", summary.epubFilesSizeMB)} Mo")
                Text("Cache temporaire : ${String.format("%.2f", summary.cacheSizeMB)} Mo")
                Text("Total utilisé : ${String.format("%.2f", summary.totalAppStorageMB)} Mo", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onClearCache,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vider le cache")
            }
        }
    }
}
