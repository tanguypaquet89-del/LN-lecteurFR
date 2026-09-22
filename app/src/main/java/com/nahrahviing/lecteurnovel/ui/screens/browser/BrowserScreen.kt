package com.nahrahviing.lecteurnovel.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nahrahviing.lecteurnovel.util.AppPreferences
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.search.SupportedPlatforms
import com.nahrahviing.lecteurnovel.ui.components.DownloadQueueDialog
import com.nahrahviing.lecteurnovel.ui.viewmodel.BrowserViewModel
import com.nahrahviing.lecteurnovel.util.DownloadStatus

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel, onNavigateToLibrary: () -> Unit) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    val prefs = remember { AppPreferences(context) }
    val isDarkOrAmoled = remember(systemInDark, prefs.readerThemeMode) {
        val mode = prefs.readerThemeMode
        mode == "DARK" || mode == "OLED" || mode == "MIDNIGHT" || (mode == "SYSTEM" && systemInDark)
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(SupportedPlatforms.list.first().homeUrl) }
    var inputUrl by remember { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("Navigateur") }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Interception de la touche / geste retour pour naviguer sur le site web sans fermer l'application
    BackHandler(enabled = true) {
        when {
            showDownloadSheet -> {
                showDownloadSheet = false
            }
            showQueueDialog -> {
                showQueueDialog = false
            }
            webViewInstance?.canGoBack() == true -> {
                webViewInstance?.goBack()
                canGoBack = webViewInstance?.canGoBack() ?: false
                canGoForward = webViewInstance?.canGoForward() ?: false
            }
            canGoBack -> {
                webViewInstance?.goBack()
            }
            else -> {
                // Si le site ne peut plus reculer, éviter de fermer brusquement l'appli
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    android.widget.Toast.makeText(
                        context,
                        "Appuyez à nouveau pour quitter",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val detectedNovel by viewModel.detectedNovel.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val debugMessage by viewModel.debugMessage.collectAsState()
    val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()
    val queueTasks by viewModel.queueTasks.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                if (debugMessage.isNotEmpty()) {
                    Text(
                        text = debugMessage,
                        color = if (debugMessage.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                // Top URL bar & controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent")
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant")
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = {
                            IconButton(onClick = {
                                var target = inputUrl.trim()
                                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                    target = "https://$target"
                                }
                                inputUrl = target
                                webViewInstance?.loadUrl(target)
                            }) {
                                Icon(Icons.Default.Home, contentDescription = "Aller", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )

                    IconButton(onClick = { showQueueDialog = true }) {
                        BadgedBox(
                            badge = {
                                if (activeDownloadsCount > 0) {
                                    Badge { Text("$activeDownloadsCount") }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "File de téléchargement",
                                tint = if (activeDownloadsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Supported Sites Chips (4 Plateformes directes demandées)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SupportedPlatforms.list.forEach { platform ->
                        val isSelected = currentUrl.contains(platform.id) || currentUrl.contains(platform.homeUrl.removePrefix("https://"))

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                inputUrl = platform.homeUrl
                                webViewInstance?.loadUrl(platform.homeUrl)
                            },
                            label = {
                                Text(
                                    "${platform.name}.fr",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Text(platform.flag, fontSize = 13.sp)
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Detected Novel Bar
            detectedNovel?.let { novel ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (novel.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = novel.coverUrl,
                                    contentDescription = novel.title,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = novel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${novel.chapters.size} chapitres détectés • ${novel.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            val activeNovelTask = remember(queueTasks, novel.originalUrl) {
                                queueTasks.find {
                                    it.novelUrl == novel.originalUrl &&
                                    (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
                                }
                            }

                            var addedToLibraryNotice by remember(novel.originalUrl) { mutableStateOf(false) }

                            // Bouton Ajouter à la bibliothèque sans télécharger
                            OutlinedButton(
                                onClick = {
                                    viewModel.addNovelToLibrary(novel) {
                                        addedToLibraryNotice = true
                                        android.widget.Toast.makeText(context, "✓ « ${novel.title} » ajouté à la bibliothèque !", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    if (addedToLibraryNotice) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (addedToLibraryNotice) "Ajouté" else "Ajouter")
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Button(
                                onClick = { showDownloadSheet = true },
                                colors = if (activeNovelTask != null) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Icon(
                                    if (activeNovelTask != null) Icons.Default.Refresh else Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (activeNovelTask != null) {
                                        if (activeNovelTask.status == DownloadStatus.QUEUED) "En file..."
                                        else "${(activeNovelTask.progress * 100).toInt()}%"
                                    } else {
                                        "Télécharger"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowContentAccess = true
                        settings.allowFileAccess = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        // Clean standard mobile Chrome user agent matching Chrome on Android to bypass Cloudflare/Next.js WebView blocking
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

                        // Sites qui intègrent déjà leur propre mode sombre natif
                        fun isNativeDarkSite(targetUrl: String?): Boolean {
                            if (targetUrl == null) return false
                            val lower = targetUrl.lowercase()
                            return lower.contains("novelfrance.fr") || lower.contains("purrfiction.io")
                        }

                        val darkCssJs = if (isDarkOrAmoled) {
                            """
                            (function() {
                                var existingStyle = document.getElementById('night-mode-css');
                                if (!existingStyle) {
                                    var style = document.createElement('style');
                                    style.id = 'night-mode-css';
                                    style.type = 'text/css';
                                    style.innerHTML = `
                                        @media (prefers-color-scheme: dark) { }
                                        html, body {
                                            background-color: #121212 !important;
                                            color: #E0E0E0 !important;
                                        }
                                        p, article, section {
                                            color: #CCCCCC !important;
                                        }
                                        h1, h2, h3, h4, h5, h6 {
                                            color: #FFFFFF !important;
                                        }
                                        a, a:visited {
                                            color: #8AB4F8 !important;
                                        }
                                        img, picture, video {
                                            opacity: 0.88 !important;
                                            filter: brightness(0.9) contrast(1.05) !important;
                                        }
                                    `;
                                    (document.head || document.documentElement).appendChild(style);
                                }
                            })();
                            """.trimIndent()
                        } else {
                            ""
                        }

                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                val lower = reqUrl.lowercase()
                                // Bloquer les scripts publicitaires / trackers qui font geler l'hydratation Next.js (comme le fait Brave)
                                if (lower.contains("googlesyndication.com") ||
                                    lower.contains("adsbygoogle") ||
                                    lower.contains("adservice.google") ||
                                    lower.contains("pagead2") ||
                                    lower.contains("analytics.ahrefs.com") ||
                                    lower.contains("googletagmanager.com/gtag/js")
                                ) {
                                    return WebResourceResponse(
                                        "text/javascript",
                                        "UTF-8",
                                        200,
                                        "OK",
                                        emptyMap(),
                                        java.io.ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false
                                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                    return false
                                }
                                return try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl))
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (darkCssJs.isNotBlank() && !isNativeDarkSite(url)) {
                                    view?.evaluateJavascript(darkCssJs, null)
                                }
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                    viewModel.setBrowserUrl(it)
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (darkCssJs.isNotBlank() && !isNativeDarkSite(url)) {
                                    view?.evaluateJavascript(darkCssJs, null)
                                }
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                    pageTitle = view?.title ?: "Navigateur"
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                    viewModel.setBrowserUrl(it)
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                if (darkCssJs.isNotBlank() && !isNativeDarkSite(url)) {
                                    view?.evaluateJavascript(darkCssJs, null)
                                }
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                    viewModel.setBrowserUrl(it)
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                            }
                        }

                        loadUrl(SupportedPlatforms.list.first().homeUrl)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Download Dialog / Status
            if (isDownloading) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = downloadStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { showQueueDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (activeDownloadsCount > 1) "File ($activeDownloadsCount)" else "File d'attente",
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.cancelCurrentDownload() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Annuler le téléchargement",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // Sheet de confirmation de téléchargement
        if (showDownloadSheet && detectedNovel != null) {
            val novel = detectedNovel!!
            DownloadDialog(
                novel = novel,
                onDismiss = { showDownloadSheet = false },
                onDownload = { format, exportMode, start, end ->
                    showDownloadSheet = false
                    viewModel.downloadNovelAdvanced(novel, format, exportMode, start, end)
                },
                onRelaunch = { format, exportMode, start, end ->
                    showDownloadSheet = false
                    viewModel.relaunchNovelDownload(novel, format, exportMode, start, end)
                },
                onAddToLibraryWithoutDownload = {
                    showDownloadSheet = false
                    viewModel.addNovelToLibrary(novel) {
                        android.widget.Toast.makeText(context, "✓ « ${novel.title} » ajouté à la bibliothèque (Streaming direct) !", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Boîte de dialogue de la file d'attente
        if (showQueueDialog) {
            DownloadQueueDialog(
                onDismiss = { showQueueDialog = false }
            )
        }
    }
}
