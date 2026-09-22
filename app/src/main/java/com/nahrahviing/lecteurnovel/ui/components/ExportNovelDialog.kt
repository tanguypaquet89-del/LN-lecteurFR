package com.nahrahviing.lecteurnovel.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nahrahviing.lecteurnovel.data.epub.ExportFormat
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportNovelDialog(
    novel: SavedNovelEntity,
    repository: NovelRepository,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(ExportFormat.EPUB) }
    var isExporting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var tempExportedFile by remember { mutableStateOf<File?>(null) }

    // Launcher pour enregistrer dans les documents de l'utilisateur
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedFormat.mimeType)
    ) { uri: Uri? ->
        if (uri != null && tempExportedFile != null) {
            coroutineScope.launch {
                try {
                    isExporting = true
                    statusText = "Enregistrement du fichier..."
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            tempExportedFile!!.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                    }
                    onMessage("✓ Fichier ${selectedFormat.label} enregistré avec succès !")
                    onDismiss()
                } catch (e: Exception) {
                    onMessage("Erreur d'enregistrement : ${e.localizedMessage}")
                } finally {
                    isExporting = false
                }
            }
        }
    }

    fun shareFile(file: File, format: ExportFormat) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, novel.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager ${novel.title} (${format.label})"))
            onDismiss()
        } catch (e: Exception) {
            onMessage("Erreur de partage : ${e.localizedMessage}")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        icon = {
            Icon(
                Icons.Default.FileDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Exporter le roman",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info roman
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
                        Text(
                            text = "${novel.totalChapters} chapitres disponibles • ${novel.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    "Choisissez le format de sortie :",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                // Choix des formats (EPUB, PDF, ODT, TXT)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = if (selectedFormat == fmt) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            },
                            onClick = { selectedFormat = fmt }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFormat == fmt,
                                    onClick = { selectedFormat = fmt }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${fmt.label} (.${fmt.extension})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = fmt.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (isExporting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusText.ifBlank { "Génération du fichier ${selectedFormat.label}..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Bouton Partager
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                isExporting = true
                                statusText = "Génération de ${selectedFormat.label}..."
                                val file = withContext(Dispatchers.IO) {
                                    repository.exportSavedNovel(novel, selectedFormat)
                                }
                                shareFile(file, selectedFormat)
                            } catch (e: Exception) {
                                onMessage("Erreur d'exportation : ${e.localizedMessage}")
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Partager")
                }

                // Bouton Enregistrer sous
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                isExporting = true
                                statusText = "Génération de ${selectedFormat.label}..."
                                val file = withContext(Dispatchers.IO) {
                                    repository.exportSavedNovel(novel, selectedFormat)
                                }
                                tempExportedFile = file
                                val safeTitle = com.nahrahviing.lecteurnovel.data.epub.EpubGenerator.getSafeTitle(novel.title)
                                saveDocumentLauncher.launch("${safeTitle}.${selectedFormat.extension}")
                            } catch (e: Exception) {
                                onMessage("Erreur d'exportation : ${e.localizedMessage}")
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enregistrer...")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Annuler")
            }
        }
    )
}
