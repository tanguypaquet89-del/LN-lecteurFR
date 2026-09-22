package com.nahrahviing.lecteurnovel.ui.screens.profile

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nahrahviing.lecteurnovel.data.local.ReadingStatEntity
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.ui.viewmodel.LibraryViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.ReaderViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ReaderViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val savedNovels by libraryViewModel.savedNovels.collectAsState()
    val readingStats by libraryViewModel.allReadingStats.collectAsState()

    // Aggregate statistics
    val totalNovels = savedNovels.size
    val totalReadingMinutes = readingStats.sumOf { it.totalReadingTimeMinutes }
    val totalChaptersFromStats = readingStats.sumOf { it.chaptersCompleted }
    val totalChaptersFromProgress = savedNovels.sumOf { it.lastReadChapterIndex }
    val totalChaptersRead = maxOf(totalChaptersFromStats, totalChaptersFromProgress)

    // Formatted reading time
    val hours = totalReadingMinutes / 60
    val minutes = totalReadingMinutes % 60
    val timeFormatted = when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes} min"
        else -> "0 min"
    }

    // Export launcher
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && settingsViewModel != null) {
            settingsViewModel.saveBackupToUri(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Google Sign-in launcher for quick cloud save
    var pendingDriveAction by remember { mutableStateOf<String?>(null) }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data == null && result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Connexion Google Drive annulée", Toast.LENGTH_SHORT).show()
            pendingDriveAction = null
            return@rememberLauncherForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null && settingsViewModel != null) {
                if (pendingDriveAction == "backup") {
                    settingsViewModel.startDriveBackup(account)
                    Toast.makeText(context, "Sauvegarde Cloud lancée...", Toast.LENGTH_SHORT).show()
                } else if (pendingDriveAction == "restore") {
                    settingsViewModel.startDriveRestore(account)
                    Toast.makeText(context, "Restauration Cloud lancée...", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur Google: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        pendingDriveAction = null
    }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Header: User avatar and title
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Profil Lecteur & Statistiques",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Suivi de lecture et sauvegarde permanente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Key Stats Grid Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMetricCard(
                        title = "Temps de lecture",
                        value = timeFormatted,
                        icon = Icons.Default.AccessTime,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Chapitres lus",
                        value = "$totalChaptersRead",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMetricCard(
                        title = "Romans suivis",
                        value = "$totalNovels",
                        icon = Icons.Default.LibraryBooks,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Statistiques actives",
                        value = "${readingStats.size}",
                        icon = Icons.Default.Analytics,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Actions: Export & Cloud Backup Quick Access
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sauvegarde & Export des Statistiques",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Vos statistiques de lecture (temps passé, chapitres lus, dates de session) sont incluses dans les sauvegardes JSON et Google Drive. Vous pouvez les exporter à tout moment ou les restaurer sur un autre appareil.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick Cloud Backup
                            Button(
                                onClick = {
                                    if (settingsViewModel != null) {
                                        val currentAcc = GoogleSignIn.getLastSignedInAccount(context)
                                        if (currentAcc != null) {
                                            settingsViewModel.startDriveBackup(currentAcc)
                                            Toast.makeText(context, "Sauvegarde Google Drive lancée...", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pendingDriveAction = "backup"
                                            val client = settingsViewModel.googleDriveSyncManager.getGoogleSignInClient(context)
                                            googleSignInLauncher.launch(client.signInIntent)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Drive Cloud", fontSize = 13.sp)
                            }

                            // Quick File Export
                            OutlinedButton(
                                onClick = {
                                    if (settingsViewModel != null) {
                                        createBackupLauncher.launch(settingsViewModel.generateDefaultBackupFileName())
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Exporter JSON", fontSize = 13.sp)
                            }
                        }

                        // Share action
                        OutlinedButton(
                            onClick = {
                                if (settingsViewModel != null) {
                                    settingsViewModel.createBackupShareIntent(
                                        onReady = { intent -> context.startActivity(intent) },
                                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Partager la sauvegarde complète (avec stats)")
                        }
                    }
                }
            }

            // Detailed Novel Stats breakdown
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Détail par roman",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${savedNovels.size} au total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (savedNovels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun roman dans votre bibliothèque pour le moment.\nOuvrez un roman dans le lecteur pour enregistrer du temps de lecture !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val statsMap = readingStats.associateBy { it.novelUrl }
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                items(savedNovels) { novel ->
                    val stat = statsMap[novel.originalUrl]
                    val novelTimeMin = stat?.totalReadingTimeMinutes ?: 0L
                    val novelTimeStr = when {
                        novelTimeMin >= 60 -> "${novelTimeMin / 60}h ${novelTimeMin % 60}m"
                        novelTimeMin > 0 -> "${novelTimeMin} min"
                        else -> "0 min"
                    }
                    val completed = maxOf(stat?.chaptersCompleted ?: 0, novel.lastReadChapterIndex)
                    val lastDate = stat?.lastSessionDate ?: novel.lastReadAt

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = novel.title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Temps : $novelTimeStr",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Chapitres : $completed / ${novel.totalChapters}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            if (lastDate > 0L) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Dernière lecture : ${sdf.format(Date(lastDate))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
