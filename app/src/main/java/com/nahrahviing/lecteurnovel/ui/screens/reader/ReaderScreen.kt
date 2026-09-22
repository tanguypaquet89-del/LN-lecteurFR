package com.nahrahviing.lecteurnovel.ui.screens.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nahrahviing.lecteurnovel.R
import com.nahrahviing.lecteurnovel.tts.ActiveTtsHighlight
import com.nahrahviing.lecteurnovel.tts.TtsPlayState
import com.nahrahviing.lecteurnovel.ui.components.NovelAudioPlayerBar
import com.nahrahviing.lecteurnovel.ui.components.RubyAccentColor
import com.nahrahviing.lecteurnovel.ui.theme.*
import com.nahrahviing.lecteurnovel.ui.viewmodel.ReaderViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.ReaderFontMode
import com.nahrahviing.lecteurnovel.ui.viewmodel.ReaderThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onNavigateBack: () -> Unit) {
    val novel by viewModel.activeNovel.collectAsState()
    val chapters by viewModel.activeChapters.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()

    val fontSize by viewModel.fontSize.collectAsState()
    val lineHeight by viewModel.lineHeight.collectAsState()
    val horizPadding by viewModel.horizontalPadding.collectAsState()
    val vertPadding by viewModel.verticalPadding.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val isLoadingChapter by viewModel.isLoadingChapter.collectAsState()
    val chapterLoadError by viewModel.chapterLoadError.collectAsState()


    // États TTS
    val isTtsBarVisible by viewModel.isTtsBarVisible.collectAsState()
    val ttsPlayState by viewModel.ttsPlayState.collectAsState()
    val ttsVoice by viewModel.ttsVoice.collectAsState()
    val ttsVoices by viewModel.ttsVoices.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsProgressMs by viewModel.ttsProgressMs.collectAsState()
    val ttsDurationMs by viewModel.ttsDurationMs.collectAsState()
    val ttsHighlight by viewModel.ttsHighlight.collectAsState()
    val ttsStatusMessage by viewModel.ttsStatusMessage.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkSnippetText by remember { mutableStateOf("") }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isTwoColumnMode by remember { mutableStateOf(false) }

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val scrollState = rememberScrollState()

    val paragraphs = remember(currentChapter?.content, novel?.title) {
        val raw = currentChapter?.content ?: ""
        if (raw.isBlank()) emptyList()
        else com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.formatToParagraphs(raw, novel?.title)
    }

    // Chargement automatique immédiat si le chapitre est en ligne et non encore téléchargé
    LaunchedEffect(currentChapterIndex, currentChapter?.content) {
        if (currentChapter != null && currentChapter.content.isBlank()) {
            viewModel.ensureChapterLoaded(currentChapterIndex)
        }
    }

    BackHandler {
        when {
            showBookmarksSheet -> showBookmarksSheet = false
            showTocSheet -> showTocSheet = false
            showSettingsSheet -> showSettingsSheet = false
            showAddBookmarkDialog -> showAddBookmarkDialog = false
            showControls -> showControls = false
            else -> onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.commitCurrentSession()
        }
    }

    // Auto-défilement intelligent vers le mot/paragraphe en cours de lecture
    LaunchedEffect(ttsHighlight?.paragraphIndex) {
        val pIdx = ttsHighlight?.paragraphIndex ?: return@LaunchedEffect
        if (pIdx in paragraphs.indices && paragraphs.isNotEmpty()) {
            val maxScroll = scrollState.maxValue
            if (maxScroll > 0) {
                val targetScroll = ((pIdx.toFloat() / paragraphs.size) * maxScroll).toInt()
                scrollState.animateScrollTo((targetScroll - 180).coerceAtLeast(0))
            }
        }
    }

    // Configuration des couleurs selon le thème
    val (backgroundColor, textColor, surfaceColor) = when (themeMode) {
        ReaderThemeMode.LIGHT -> Triple(LightReaderBackground, LightReaderOnBackground, LightReaderSurface)
        ReaderThemeMode.SEPIA -> Triple(SepiaBackground, SepiaOnBackground, SepiaSurface)
        ReaderThemeMode.DARK -> Triple(DarkReaderBackground, DarkReaderOnBackground, DarkReaderSurface)
        ReaderThemeMode.OLED -> Triple(OledReaderBackground, OledReaderOnBackground, OledReaderSurface)
        ReaderThemeMode.MINT -> Triple(MintReaderBackground, MintReaderOnBackground, MintReaderSurface)
        ReaderThemeMode.MIDNIGHT -> Triple(MidnightReaderBackground, MidnightReaderOnBackground, MidnightReaderSurface)
    }

    val selectedFontFamily = when (fontMode) {
        ReaderFontMode.LORA -> FontFamily(Font(R.font.lora, FontWeight.Normal))
        ReaderFontMode.MERRIWEATHER -> FontFamily(Font(R.font.merriweather, FontWeight.Normal))
        ReaderFontMode.ROBOTO_SERIF -> FontFamily(Font(R.font.roboto_serif, FontWeight.Normal))
        ReaderFontMode.CINZEL -> FontFamily(Font(R.font.cinzel, FontWeight.Normal))
        ReaderFontMode.SERIF, ReaderFontMode.GARAMOND, ReaderFontMode.BASKERVILLE, ReaderFontMode.PALATINO -> FontFamily.Serif
        ReaderFontMode.SANS_SERIF, ReaderFontMode.TREBUCHET -> FontFamily.SansSerif
        ReaderFontMode.MONOSPACE -> FontFamily.Monospace
        ReaderFontMode.CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable { showControls = !showControls }
    ) {
        val isTablet = maxWidth >= 760.dp
        val is14InchTablet = maxWidth >= 1100.dp
        val horizontalScreenPadding = if (is14InchTablet) (horizPadding + 32).dp else if (isTablet) (horizPadding + 16).dp else horizPadding.dp

        // Contenu du chapitre centré ou en mode deux colonnes pour grand écran
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (isTwoColumnMode && isTablet) 1500.dp else if (is14InchTablet) 920.dp else if (isTablet) 820.dp else androidx.compose.ui.unit.Dp.Unspecified)
                    .padding(
                        horizontal = horizontalScreenPadding,
                        vertical = vertPadding.dp + if (isTtsBarVisible) 110.dp else 56.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentChapter != null) {
                    Text(
                        text = currentChapter.title,
                        fontSize = (fontSize + 4).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = selectedFontFamily,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    )

                    if (isLoadingChapter) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Lecture en ligne : chargement du chapitre...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    } else if (paragraphs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = chapterLoadError ?: "Ce chapitre n'a pas encore été téléchargé.",
                                    fontSize = fontSize.sp,
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.ensureChapterLoaded(currentChapterIndex) }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Charger le chapitre en ligne")
                                }
                            }
                        }
                    } else if (isTablet && isTwoColumnMode) {
                        // Mode Deux Colonnes (Style livre ouvert sur tablette 10" à 14")
                        val mid = (paragraphs.size + 1) / 2
                        val leftList = paragraphs.subList(0, mid)
                        val rightList = paragraphs.subList(mid, paragraphs.size)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                leftList.forEachIndexed { idx, paraText ->
                                    ParagraphItem(
                                        paraText = paraText,
                                        pIndex = idx,
                                        ttsHighlight = ttsHighlight,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        selectedFontFamily = selectedFontFamily,
                                        textColor = textColor,
                                        onParagraphClick = { viewModel.startTtsForCurrentChapter(paragraphs, idx) },
                                        onParagraphLongClick = { bookmarkSnippetText = paraText.take(100) + "..."; showAddBookmarkDialog = true }
                                    )
                                }
                            }

                            // Séparateur vertical délicat style pliure de livre
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(textColor.copy(alpha = 0.12f))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                rightList.forEachIndexed { idx, paraText ->
                                    val realIdx = mid + idx
                                    ParagraphItem(
                                        paraText = paraText,
                                        pIndex = realIdx,
                                        ttsHighlight = ttsHighlight,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        selectedFontFamily = selectedFontFamily,
                                        textColor = textColor,
                                        onParagraphClick = { viewModel.startTtsForCurrentChapter(paragraphs, realIdx) },
                                        onParagraphLongClick = { bookmarkSnippetText = paraText.take(100) + "..."; showAddBookmarkDialog = true }
                                    )
                                }
                            }
                        }
                    } else {
                        // Mode Colonne Unique élégamment centrée avec confort typographique optimal
                        Column(modifier = Modifier.fillMaxWidth()) {
                            paragraphs.forEachIndexed { pIndex, paraText ->
                                ParagraphItem(
                                    paraText = paraText,
                                    pIndex = pIndex,
                                    ttsHighlight = ttsHighlight,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                    selectedFontFamily = selectedFontFamily,
                                    textColor = textColor,
                                    onParagraphClick = { viewModel.startTtsForCurrentChapter(paragraphs, pIndex) },
                                    onParagraphLongClick = { bookmarkSnippetText = paraText.take(100) + "..."; showAddBookmarkDialog = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Navigation bas de page
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { viewModel.setChapterIndex(currentChapterIndex - 1) },
                            enabled = currentChapterIndex > 0
                        ) {
                            Text("Précédent")
                        }

                        Button(
                            onClick = { viewModel.setChapterIndex(currentChapterIndex + 1) },
                            enabled = currentChapterIndex < chapters.size - 1
                        ) {
                            Text("Suivant")
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aucun chapitre sélectionné", color = textColor)
                    }
                }
            }
        }

        // Bandeau Audio TTS Flottant (Design inspiré de Novel France, centré et proportionnel)
        AnimatedVisibility(
            visible = isTtsBarVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .padding(top = if (showControls) 64.dp else 12.dp)
                .zIndex(10f)
        ) {
            NovelAudioPlayerBar(
                playState = ttsPlayState,
                currentVoice = ttsVoice,
                availableVoices = ttsVoices,
                speed = ttsSpeed,
                progressMs = ttsProgressMs,
                durationMs = ttsDurationMs,
                statusMessage = ttsStatusMessage,
                onPlayPause = { viewModel.playPauseTts() },
                onSeekTo = { viewModel.seekTts(it) },
                onSkipBackward = { viewModel.skipBackwardTts() },
                onSkipForward = { viewModel.skipForwardTts() },
                onSpeedChange = { viewModel.setTtsSpeed(it) },
                onVoiceSelect = { viewModel.setTtsVoice(it) },
                onClose = { viewModel.hideTtsBar() }
            )
        }

        // Top bar overlay
        if (showControls) {
            Surface(
                color = surfaceColor.copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = textColor)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = novel?.title ?: "Lecteur",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                        Text(
                            text = currentChapter?.title ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }

                    // Bascule Mode Livre 2 colonnes (sur tablettes et grands écrans)
                    if (isTablet) {
                        IconButton(onClick = { isTwoColumnMode = !isTwoColumnMode }) {
                            Icon(
                                imageVector = if (isTwoColumnMode) Icons.Default.ViewAgenda else Icons.Default.VerticalSplit,
                                contentDescription = if (isTwoColumnMode) "Mode 1 colonne" else "Mode 2 colonnes (Livre)",
                                tint = if (isTwoColumnMode) RubyAccentColor else textColor
                            )
                        }
                    }

                    // Bouton d'activation de la lecture vocale réaliste (TTS)
                    IconButton(onClick = {
                        if (isTtsBarVisible) {
                            viewModel.hideTtsBar()
                        } else {
                            viewModel.startTtsForCurrentChapter(paragraphs, 0)
                        }
                    }) {
                        Icon(
                            imageVector = if (isTtsBarVisible && ttsPlayState == TtsPlayState.PLAYING) Icons.Default.GraphicEq else Icons.Default.Headphones,
                            contentDescription = "Lecture vocale (TTS)",
                            tint = if (isTtsBarVisible) RubyAccentColor else textColor
                        )
                    }

                    IconButton(onClick = { showBookmarksSheet = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Marque-pages", tint = textColor)
                    }

                    IconButton(onClick = { showTocSheet = true }) {

                        Icon(Icons.Default.FormatListBulleted, contentDescription = "Table des matières", tint = textColor)
                    }

                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres de lecture", tint = textColor)
                    }
                }
            }
        }


        // Bookmarks Sheet
        if (showBookmarksSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBookmarksSheet = false },
                containerColor = surfaceColor
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Marque-pages (${bookmarks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                        itemsIndexed(bookmarks) { _, bookmark ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        bookmark.chapterTitle,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        bookmark.snippet,
                                        color = textColor.copy(alpha = 0.7f),
                                        maxLines = 2
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    viewModel.setChapterIndex(bookmark.chapterIndex)
                                    showBookmarksSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Add Bookmark Dialog
        if (showAddBookmarkDialog) {
            AlertDialog(
                onDismissRequest = { showAddBookmarkDialog = false },
                title = { Text("Ajouter un marque-page") },
                text = {
                    Column {
                        Text("Voulez-vous ajouter ce passage aux marque-pages ?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            bookmarkSnippetText,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addBookmark(bookmarkSnippetText, 0)
                        showAddBookmarkDialog = false
                    }) {
                        Text("Ajouter")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBookmarkDialog = false }) {
                        Text("Annuler")
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = textColor,
                textContentColor = textColor
            )
        }

        // Table des matières Sheet
        if (showTocSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                containerColor = surfaceColor
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Table des matières (${chapters.size} chapitres)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                        itemsIndexed(chapters) { index, chap ->
                            val isCurrent = index == currentChapterIndex
                            ListItem(
                                headlineContent = {
                                    Text(
                                        chap.title,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else textColor
                                    )
                                },
                                modifier = Modifier.clickable {
                                    viewModel.setChapterIndex(index)
                                    showTocSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Paramètres de lecture Sheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = surfaceColor
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Paramètres d'affichage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Taille police
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Taille : ${fontSize.toInt()}sp", color = textColor, modifier = Modifier.width(100.dp))
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..32f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Interligne
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Interligne : ${String.format("%.1f", lineHeight)}", color = textColor, modifier = Modifier.width(100.dp))
                        Slider(
                            value = lineHeight,
                            onValueChange = { viewModel.setLineHeight(it) },
                            valueRange = 1.2f..2.5f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Thème du lecteur
                    Text("Thème", fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderThemeMode.values().take(4).forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParagraphItem(
    paraText: String,
    pIndex: Int,
    ttsHighlight: ActiveTtsHighlight?,
    fontSize: Float,
    lineHeight: Float,
    selectedFontFamily: FontFamily,
    textColor: Color,
    onParagraphClick: () -> Unit,
    onParagraphLongClick: () -> Unit
) {
    val isCurrentP = ttsHighlight?.paragraphIndex == pIndex

    val annotatedString = remember(paraText, isCurrentP, ttsHighlight) {
        if (isCurrentP && ttsHighlight != null && ttsHighlight.charStart >= 0) {
            val start = ttsHighlight.charStart.coerceIn(0, paraText.length)
            val end = ttsHighlight.charEnd.coerceIn(start, paraText.length)

            buildAnnotatedString {
                if (start > 0) {
                    append(paraText.substring(0, start))
                }
                if (start < end) {
                    withStyle(
                        SpanStyle(
                            background = RubyAccentColor.copy(alpha = 0.28f),
                            color = RubyAccentColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(paraText.substring(start, end))
                    }
                }
                if (end < paraText.length) {
                    append(paraText.substring(end))
                }
            }
        } else {
            buildAnnotatedString { append(paraText) }
        }
    }

    Surface(
        color = if (isCurrentP) RubyAccentColor.copy(alpha = 0.05f) else Color.Transparent,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onParagraphClick,
                onLongClick = onParagraphLongClick
            )
    ) {
        Text(
            text = annotatedString,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeight).sp,
            fontFamily = selectedFontFamily,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

