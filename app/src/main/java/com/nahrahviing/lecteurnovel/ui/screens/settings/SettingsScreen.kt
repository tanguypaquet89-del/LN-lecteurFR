package com.nahrahviing.lecteurnovel.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.app.Activity
import com.nahrahviing.lecteurnovel.data.backup.BackupValidationResult
import com.nahrahviing.lecteurnovel.data.network.parsers.dynamic.DynamicParserConfig
import com.nahrahviing.lecteurnovel.ui.screens.settings.components.*
import com.nahrahviing.lecteurnovel.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val storageSummary by viewModel.storageSummary.collectAsState()
    val isAutoUpdateEnabled by viewModel.isAutoUpdateEnabled.collectAsState()
    val lastCheckTimestamp by viewModel.lastCheckTimestamp.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val dynamicParsers by viewModel.dynamicParsers.collectAsState()
    val isProcessingBackup by viewModel.isProcessingBackup.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
    val recoverableAuthIntent by viewModel.recoverableAuthIntent.collectAsState()
    val isAutoDriveBackupEnabled by viewModel.isAutoDriveBackupEnabled.collectAsState()
    val isAutoEpubSyncEnabled by viewModel.isAutoEpubSyncEnabled.collectAsState()
    val lastDriveBackupTimestamp by viewModel.lastDriveBackupTimestamp.collectAsState()
    val lastDriveBackupStatus by viewModel.lastDriveBackupStatus.collectAsState()
    val driveConnectedEmail by viewModel.driveConnectedEmail.collectAsState()
    val downloadChapterDelayMs by viewModel.downloadChapterDelayMs.collectAsState()
    val autoDownloadOnRestore by viewModel.autoDownloadOnRestore.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showDriveConfigDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedParserForView by remember { mutableStateOf<DynamicParserConfig?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val p2pPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.retryPendingDriveAction()
        } else {
            snackbarMessage = "Autorisation Google Drive refusée ou annulée."
        }
        viewModel.clearRecoverableAuthIntent()
    }

    LaunchedEffect(recoverableAuthIntent) {
        recoverableAuthIntent?.let { intent ->
            recoverableAuthLauncher.launch(intent)
        }
    }
    
    var pendingDriveAction by remember { mutableStateOf<String?>(null) }
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data == null && result.resultCode != Activity.RESULT_OK) {
            snackbarMessage = "Connexion Google annulée."
            pendingDriveAction = null
            return@rememberLauncherForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                when (pendingDriveAction) {
                    "backup" -> viewModel.startDriveBackup(account)
                    "restore" -> viewModel.startDriveRestore(account)
                    "sync_epubs" -> viewModel.syncAllEpubs(account)
                    "enable_auto_backup" -> viewModel.setAutoDriveBackupEnabled(true, account)
                }
            } else {
                snackbarMessage = "Aucun compte sélectionné."
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val details = when (e.statusCode) {
                10 -> "Erreur 10 (DEVELOPER_ERROR) : Vérifiez que l'API Google Drive est bien activée dans Google Cloud Console et que l'ID Client Android a le SHA-1 (5A:57:81:B7:9C:64:CB:D1:E7:47:C4:DC:A5:B7:5E:5B:0E:F2:89:28) et le package (com.nahrahviing.lecteurnovel)."
                12500 -> "Erreur 12500 (SIGN_IN_FAILED) : Dans Google Cloud Console > Écran de consentement OAuth, vérifiez que votre adresse e-mail Google est bien ajoutée dans 'Utilisateurs test'."
                12501 -> "Sélection de compte annulée."
                12502 -> "Connexion déjà en cours."
                else -> "Erreur Google (${e.statusCode}) : ${e.message ?: "Échec de connexion"}"
            }
            snackbarMessage = details
        } catch (e: Exception) {
            snackbarMessage = "Erreur de connexion Google : ${e.message}"
        }
        pendingDriveAction = null
    }

    LaunchedEffect(backupStatusMessage) {
        backupStatusMessage?.let { msg ->
            if (msg.isNotBlank()) {
                snackbarMessage = msg
            }
        }
    }

    var pendingP2PAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    val p2pPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingP2PAction?.invoke()
        } else {
            snackbarMessage = "Permissions requises pour le transfert P2P (Bluetooth et Localisation)."
        }
        pendingP2PAction = null
    }


    // Dialogs et launchers pour la sauvegarde / restauration
    var showPasteBackupDialog by remember { mutableStateOf(false) }
    var pasteBackupJsonText by remember { mutableStateOf("") }
    var pasteBackupValidation by remember { mutableStateOf<BackupValidationResult?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.saveBackupToUri(uri) { success, msg ->
                snackbarMessage = msg
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromUri(uri) { result ->
                snackbarMessage = if (result.success) {
                    "✓ Restauration réussie : ${result.restoredNovelsCount} roman(s) restauré(s) !"
                } else {
                    result.message
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = {
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) {
                            Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section Parseurs Dynamiques JSON
                DynamicParsersSection(
                    parsers = dynamicParsers,
                    onViewParser = { selectedParserForView = it },
                    onDeleteParser = { parserId ->
                        val parser = dynamicParsers.find { it.id == parserId }
                        viewModel.deleteParser(parserId)
                        snackbarMessage = "Parseur '${parser?.name ?: parserId}' supprimé."
                    },
                    onImportClick = {
                        importErrorMessage = null
                        importJsonText = ""
                        showImportDialog = true
                    },
                    onResetToDefault = {
                        viewModel.resetParsersToDefault()
                        snackbarMessage = "Parseurs réinitialisés aux versions par défaut."
                    }
                )

                // Section Gestion des téléchargements (temporisation / débit)
                DownloadPacingCard(
                    delayMs = downloadChapterDelayMs,
                    onDelayChange = { viewModel.setDownloadChapterDelayMs(it) }
                )

                // Section Mises à jour des chapitres
                ChapterUpdatesCard(
                    isAutoUpdateEnabled = isAutoUpdateEnabled,
                    onToggleAutoUpdate = { viewModel.setAutoUpdateEnabled(it) },
                    lastCheckTimestamp = lastCheckTimestamp,
                    updateCheckResult = updateCheckResult,
                    isCheckingUpdates = isCheckingUpdates,
                    onCheckForUpdatesNow = { viewModel.checkForUpdatesNow() }
                )

                // Section Synchronisation & Sauvegarde
                BackupSection(
                    driveConnectedEmail = driveConnectedEmail,
                    lastDriveBackupTimestamp = lastDriveBackupTimestamp,
                    lastDriveBackupStatus = lastDriveBackupStatus,
                    isAutoDriveBackupEnabled = isAutoDriveBackupEnabled,
                    isAutoEpubSyncEnabled = isAutoEpubSyncEnabled,
                    isAutoDownloadOnRestoreEnabled = autoDownloadOnRestore,
                    isProcessingBackup = isProcessingBackup,
                    backupStatusMessage = backupStatusMessage,
                    onShowDriveConfigDialog = { showDriveConfigDialog = true },
                    onToggleAutoDriveBackup = { isChecked ->
                        if (isChecked) {
                            val currentAcc = GoogleSignIn.getLastSignedInAccount(context)
                            if (currentAcc != null) {
                                viewModel.setAutoDriveBackupEnabled(true, currentAcc)
                            } else {
                                pendingDriveAction = "enable_auto_backup"
                                val client = viewModel.googleDriveSyncManager.getGoogleSignInClient(context)
                                googleSignInLauncher.launch(client.signInIntent)
                            }
                        } else {
                            viewModel.setAutoDriveBackupEnabled(false)
                        }
                    },
                    onToggleAutoEpubSync = { isChecked ->
                        viewModel.setAutoEpubSyncEnabled(isChecked)
                    },
                    onToggleAutoDownloadOnRestore = { isChecked ->
                        viewModel.setAutoDownloadOnRestore(isChecked)
                    },
                    onRestoreDriveClick = {
                        pendingDriveAction = "restore"
                        val client = viewModel.googleDriveSyncManager.getGoogleSignInClient(context)
                        googleSignInLauncher.launch(client.signInIntent)
                    },
                    onBackupDriveClick = {
                        pendingDriveAction = "backup"
                        val client = viewModel.googleDriveSyncManager.getGoogleSignInClient(context)
                        googleSignInLauncher.launch(client.signInIntent)
                    },
                    onSyncAllEpubsClick = {
                        pendingDriveAction = "sync_epubs"
                        val client = viewModel.googleDriveSyncManager.getGoogleSignInClient(context)
                        googleSignInLauncher.launch(client.signInIntent)
                    },
                    onP2PReceiveClick = {
                        pendingP2PAction = { viewModel.startP2PReceive() }
                        p2pPermissionLauncher.launch(p2pPermissions)
                    },
                    onP2PSendClick = {
                        pendingP2PAction = { viewModel.startP2PSend() }
                        p2pPermissionLauncher.launch(p2pPermissions)
                    },
                    onImportJsonClick = { showPasteBackupDialog = true },
                    onShareBackupClick = {
                        viewModel.createBackupShareIntent(
                            onReady = { intent -> context.startActivity(intent) },
                            onError = { err -> snackbarMessage = err }
                        )
                    },
                    onExportBackupClick = {
                        createBackupLauncher.launch(viewModel.generateDefaultBackupFileName())
                    }
                )

                // Section Stockage
                StorageSettingsCard(
                    storageSummary = storageSummary,
                    onClearCache = { viewModel.clearCache() }
                )

            // Section Sources supportées
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Plateformes de lecture prises en charge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• NovelFrance.fr 🇫🇷 (API directe)")
                    Text("• ChiReads.com 🇨🇳 (JSON dynamique)")
                    Text("• Purrfiction.io 🐱 (API directe)")
                    Text("• Soreyawari.com 🌸 (JSON dynamique)")
                    Text("• RoyalRoad.com 👑 (JSON dynamique)")
                    Text("• WuxiaWorld.com ⚔️ (JSON dynamique)")
                    Text("• Trad-Index 🔍 (Indexeur francophone)")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Grâce aux parseurs dynamiques, vous pouvez ajouter n'importe quel autre site de webnovels sans recompiler l'application.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

    // Dialog pour voir la configuration JSON d'un parseur
    selectedParserForView?.let { parser ->
        AlertDialog(
            onDismissRequest = { selectedParserForView = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(parser.flag)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${parser.name} (${parser.id}.json)")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        parser.description.ifEmpty { parser.baseUrl },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = parser.toJson(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(parser.toJson()))
                        snackbarMessage = "JSON copié dans le presse-papier !"
                        selectedParserForView = null
                    }
                ) {
                    Text("Copier JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedParserForView = null }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Dialog pour importer un parseur JSON
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Importer un parseur JSON") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Collez la définition JSON du parseur ci-dessous avec les sélecteurs CSS :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importErrorMessage = null
                        },
                        label = { Text("Configuration JSON") },
                        placeholder = {
                            Text(
                                "{\n  \"id\": \"monsite\",\n  \"name\": \"Mon Site\",\n  \"baseUrl\": \"https://monsite.com\",\n  \"urlPatterns\": [\"monsite.com\"],\n  \"selectors\": {\n    \"title\": \"h1\",\n    \"chapters\": {\"list\": \"a.chapitre\"},\n    \"content\": {\"body\": \"div.text p\"}\n  }\n}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )

                    importErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isBlank()) {
                            importErrorMessage = "Veuillez coller un JSON valide."
                            return@Button
                        }
                        val (success, message) = viewModel.importParserJson(importJsonText)
                        if (success) {
                            snackbarMessage = message
                            showImportDialog = false
                        } else {
                            importErrorMessage = message
                        }
                    }
                ) {
                    Text("Valider l'import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialog pour coller et restaurer un JSON de sauvegarde
    if (showPasteBackupDialog) {
        AlertDialog(
            onDismissRequest = { showPasteBackupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restaurer depuis un texte JSON")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Collez le contenu JSON de votre sauvegarde :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = pasteBackupJsonText,
                        onValueChange = {
                            pasteBackupJsonText = it
                            pasteBackupValidation = null
                        },
                        label = { Text("Données JSON") },
                        placeholder = { Text("{\n  \"version\": 1,\n  \"novels\": [...]\n}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val clipboardText = clipboardManager.getText()?.text
                                if (!clipboardText.isNullOrBlank()) {
                                    pasteBackupJsonText = clipboardText
                                    pasteBackupValidation = viewModel.validateBackupJson(clipboardText)
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Coller presse-papier", fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                if (pasteBackupJsonText.isNotBlank()) {
                                    pasteBackupValidation = viewModel.validateBackupJson(pasteBackupJsonText)
                                }
                            }
                        ) {
                            Text("Analyser", fontSize = 12.sp)
                        }
                    }

                    pasteBackupValidation?.let { valResult ->
                        if (valResult.isValid && valResult.backup != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "✓ Fichier de sauvegarde valide !",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "• Romans : ${valResult.novelsCount}\n• Signets : ${valResult.bookmarksCount}\n• Stats de lecture : ${valResult.readingStatsCount}\n• Exporté le : ${valResult.exportedDateFormatted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = valResult.errorMessage ?: "JSON invalide.",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validation = pasteBackupValidation ?: viewModel.validateBackupJson(pasteBackupJsonText)
                        if (validation.isValid && validation.backup != null) {
                            viewModel.restoreFromJsonText(pasteBackupJsonText) { res ->
                                snackbarMessage = if (res.success) {
                                    "✓ Restauration réussie : ${res.restoredNovelsCount} roman(s) restauré(s) !"
                                } else {
                                    res.message
                                }
                            }
                            showPasteBackupDialog = false
                        } else {
                            pasteBackupValidation = validation
                        }
                    },
                    enabled = pasteBackupJsonText.isNotBlank()
                ) {
                    Text("Restaurer les lectures")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteBackupDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showDriveConfigDialog) {
        AlertDialog(
            onDismissRequest = { showDriveConfigDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configuration Google Cloud", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Pour que Google autorise la connexion, vérifiez ces 4 points dans console.cloud.google.com :",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("1. API Google Drive", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("Dans 'API et services' > 'Bibliothèque', recherchez 'Google Drive API' et cliquez sur 'Activer'.", style = MaterialTheme.typography.bodySmall)

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text("2. Utilisateurs test", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("Dans 'Écran de consentement OAuth' > section 'Utilisateurs test', votre compte Gmail doit être ajouté.", style = MaterialTheme.typography.bodySmall)

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text("3. Champ d'application (Scope)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("Dans 'Écran de consentement OAuth' > 'Champs d'application', ajoutez l'accès Drive AppData :", style = MaterialTheme.typography.bodySmall)
                            Text("https://www.googleapis.com/auth/drive.appdata", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text("4. Identifiant Client OAuth (Type Android)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            
                            Text("ID Client Web / OAuth :", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    com.nahrahviing.lecteurnovel.BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(com.nahrahviing.lecteurnovel.BuildConfig.GOOGLE_DRIVE_CLIENT_ID))
                                    snackbarMessage = "Client ID copié !"
                                }) {
                                    Text("Copier", fontSize = 11.sp)
                                }
                            }

                            Text("Nom de package :", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("com.nahrahviing.lecteurnovel", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString("com.nahrahviing.lecteurnovel"))
                                    snackbarMessage = "Nom de package copié !"
                                }) {
                                    Text("Copier", fontSize = 11.sp)
                                }
                            }

                            Text("Empreinte SHA-1 :", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("5A:57:81:B7:9C:64:CB:D1:E7:47:C4:DC:A5:B7:5E:5B:0E:F2:89:28", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString("5A:57:81:B7:9C:64:CB:D1:E7:47:C4:DC:A5:B7:5E:5B:0E:F2:89:28"))
                                    snackbarMessage = "SHA-1 copié !"
                                }) {
                                    Text("Copier", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDriveConfigDialog = false }) {
                    Text("Compris")
                }
            }
        )
    }
}
