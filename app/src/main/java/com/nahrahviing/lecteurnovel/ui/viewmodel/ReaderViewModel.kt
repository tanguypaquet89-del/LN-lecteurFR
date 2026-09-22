package com.nahrahviing.lecteurnovel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.nahrahviing.lecteurnovel.data.local.BookmarkEntity
import com.nahrahviing.lecteurnovel.data.local.ChapterEntity
import com.nahrahviing.lecteurnovel.data.local.ReadingStatEntity
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import com.nahrahviing.lecteurnovel.data.network.parsers.ParserManager
import com.nahrahviing.lecteurnovel.tts.ActiveTtsHighlight
import com.nahrahviing.lecteurnovel.tts.TtsPlayState
import com.nahrahviing.lecteurnovel.tts.UnifiedNovelTtsPlayer
import com.nahrahviing.lecteurnovel.tts.VoiceOption
import com.nahrahviing.lecteurnovel.util.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReaderThemeMode {
    LIGHT, SEPIA, DARK, OLED, MINT, MIDNIGHT
}

enum class ReaderFontMode {
    SYSTEM, LORA, MERRIWEATHER, ROBOTO_SERIF, CINZEL, SERIF, SANS_SERIF, MONOSPACE, CURSIVE, GARAMOND, BASKERVILLE, TREBUCHET, PALATINO
}

@HiltViewModel
class ReaderViewModel @Inject constructor(private val repository: NovelRepository, private val application: Application) : ViewModel() {
    
    val preferences = AppPreferences(application)

    // Moteur TTS unifié et ultra-réaliste
    val ttsPlayer = UnifiedNovelTtsPlayer(application)
    val ttsPlayState: StateFlow<TtsPlayState> = ttsPlayer.playState
    val ttsVoice: StateFlow<VoiceOption> = ttsPlayer.currentVoice
    val ttsVoices: StateFlow<List<VoiceOption>> = ttsPlayer.availableVoices
    val ttsSpeed: StateFlow<Float> = ttsPlayer.playbackSpeed
    val ttsProgressMs: StateFlow<Long> = ttsPlayer.progressMs
    val ttsDurationMs: StateFlow<Long> = ttsPlayer.durationMs
    val ttsHighlight: StateFlow<ActiveTtsHighlight?> = ttsPlayer.activeHighlight
    val ttsStatusMessage: StateFlow<String> = ttsPlayer.statusMessage

    private val _isTtsBarVisible = MutableStateFlow(false)
    val isTtsBarVisible: StateFlow<Boolean> = _isTtsBarVisible.asStateFlow()

    private val _activeNovel = MutableStateFlow<SavedNovelEntity?>(null)
    val activeNovel: StateFlow<SavedNovelEntity?> = _activeNovel.asStateFlow()

    private val _activeChapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val activeChapters: StateFlow<List<ChapterEntity>> = _activeChapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _isLoadingChapter = MutableStateFlow(false)
    val isLoadingChapter: StateFlow<Boolean> = _isLoadingChapter.asStateFlow()

    private val _chapterLoadError = MutableStateFlow<String?>(null)
    val chapterLoadError: StateFlow<String?> = _chapterLoadError.asStateFlow()


    private val _fontSize = MutableStateFlow(preferences.readerFontSize)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _lineHeight = MutableStateFlow(preferences.readerLineHeight)
    val lineHeight: StateFlow<Float> = _lineHeight.asStateFlow()

    private val _horizontalPadding = MutableStateFlow(preferences.readerHorizontalPadding)
    val horizontalPadding: StateFlow<Int> = _horizontalPadding.asStateFlow()

    private val _verticalPadding = MutableStateFlow(preferences.readerVerticalPadding)
    val verticalPadding: StateFlow<Int> = _verticalPadding.asStateFlow()

    private val _themeMode = MutableStateFlow(
        try { ReaderThemeMode.valueOf(preferences.readerThemeMode) } catch (_: Exception) { ReaderThemeMode.DARK }
    )
    val themeMode: StateFlow<ReaderThemeMode> = _themeMode.asStateFlow()

    private val _fontMode = MutableStateFlow(
        try { ReaderFontMode.valueOf(preferences.readerFontMode) } catch (_: Exception) { ReaderFontMode.SYSTEM }
    )
    val fontMode: StateFlow<ReaderFontMode> = _fontMode.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    private val _readingStats = MutableStateFlow<ReadingStatEntity?>(null)
    val readingStats: StateFlow<ReadingStatEntity?> = _readingStats.asStateFlow()

    private var sessionStartTime: Long = 0L

    fun openReader(novelUrl: String, startChapterIndex: Int? = null) {
        commitCurrentSession()
        sessionStartTime = System.currentTimeMillis()
        viewModelScope.launch {
            val novel = repository.getSavedNovelByUrl(novelUrl)
            if (novel != null) {
                _activeNovel.value = novel
                var chapters = repository.getChaptersForNovel(novel.originalUrl)
                if (chapters.isEmpty()) {
                    try {
                        val extracted = ParserManager.extractNovel(novel.originalUrl)
                        if (extracted != null && extracted.chapters.isNotEmpty()) {
                            repository.addNovelToLibraryWithoutDownload(extracted)
                            chapters = repository.getChaptersForNovel(novel.originalUrl)
                        }
                    } catch (_: Exception) {}
                }
                _activeChapters.value = chapters
                val idx = (startChapterIndex ?: novel.lastReadChapterIndex).coerceIn(0, maxOf(0, chapters.size - 1))
                _currentChapterIndex.value = idx
                loadBookmarks(novel.originalUrl)
                _readingStats.value = repository.getReadingStats(novel.originalUrl)
                ensureChapterLoaded(idx)
            }
        }
    }
    
    fun openReader(novel: SavedNovelEntity, startChapterIndex: Int? = null) {
        commitCurrentSession()
        sessionStartTime = System.currentTimeMillis()
        viewModelScope.launch {
            _activeNovel.value = novel
            var chapters = repository.getChaptersForNovel(novel.originalUrl)
            if (chapters.isEmpty()) {
                try {
                    val extracted = ParserManager.extractNovel(novel.originalUrl)
                    if (extracted != null && extracted.chapters.isNotEmpty()) {
                        repository.addNovelToLibraryWithoutDownload(extracted)
                        chapters = repository.getChaptersForNovel(novel.originalUrl)
                    }
                } catch (_: Exception) {}
            }
            _activeChapters.value = chapters
            val idx = (startChapterIndex ?: novel.lastReadChapterIndex).coerceIn(0, maxOf(0, chapters.size - 1))
            _currentChapterIndex.value = idx
            loadBookmarks(novel.originalUrl)
            _readingStats.value = repository.getReadingStats(novel.originalUrl)
            ensureChapterLoaded(idx)
        }
    }


    fun ensureChapterLoaded(chapterIndex: Int = _currentChapterIndex.value) {
        val chapters = _activeChapters.value
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        val novel = _activeNovel.value ?: return

        // Si le chapitre est déjà chargé
        if (chapter.content.isNotBlank()) {
            // Déjà chargé en mémoire locale, on précharge le suivant en avance
            preloadNextChapter(chapterIndex + 1)
            return
        }

        viewModelScope.launch {
            _isLoadingChapter.value = true
            _chapterLoadError.value = null
            try {
                var chapterUrl = chapter.url
                // Si l'URL était vide, essayer de ré-extraire la liste
                if (chapterUrl.isBlank()) {
                    val extracted = ParserManager.extractNovel(novel.originalUrl)
                    val freshChap = extracted?.chapters?.firstOrNull { it.index == chapter.chapterIndex }
                    if (freshChap != null && freshChap.url.isNotBlank()) {
                        chapterUrl = freshChap.url
                    }
                }

                if (chapterUrl.isBlank()) {
                    _chapterLoadError.value = "URL du chapitre introuvable."
                    return@launch
                }

                val fetched = ParserManager.fetchChapterContent(chapterUrl, novel.title)
                if (fetched.startsWith("Erreur") || fetched.isBlank()) {
                    _chapterLoadError.value = "Impossible de charger le chapitre en ligne (site indisponible ou réseau coupé)."
                } else {
                    val cleaned = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(fetched, novel.title)
                    repository.updateChapterContent(novel.originalUrl, chapter.chapterIndex, cleaned, isDownloaded = true)
                    // Mettre à jour en mémoire vive immédiatement
                    val updatedChapters = _activeChapters.value.toMutableList()
                    val idxInList = updatedChapters.indexOfFirst { it.chapterIndex == chapter.chapterIndex }
                    if (idxInList != -1) {
                        updatedChapters[idxInList] = chapter.copy(content = cleaned, url = chapterUrl, isDownloaded = true)
                        _activeChapters.value = updatedChapters
                    }
                    // Préchargement transparent du chapitre suivant
                    preloadNextChapter(chapterIndex + 1)
                }
            } catch (e: Exception) {
                _chapterLoadError.value = e.localizedMessage ?: "Erreur réseau lors du chargement du chapitre."
            } finally {
                _isLoadingChapter.value = false
            }
        }
    }

    private fun preloadNextChapter(nextIndex: Int) {
        val chapters = _activeChapters.value
        val nextChapter = chapters.getOrNull(nextIndex) ?: return
        val novel = _activeNovel.value ?: return

        if (nextChapter.content.isNotBlank() || nextChapter.url.isBlank()) {
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fetched = ParserManager.fetchChapterContent(nextChapter.url, novel.title)
                if (!fetched.startsWith("Erreur") && fetched.isNotBlank()) {
                    val cleaned = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(fetched, novel.title)
                    repository.updateChapterContent(novel.originalUrl, nextChapter.chapterIndex, cleaned, isDownloaded = true)
                    val updated = _activeChapters.value.toMutableList()
                    val idx = updated.indexOfFirst { it.chapterIndex == nextChapter.chapterIndex }
                    if (idx != -1) {
                        updated[idx] = nextChapter.copy(content = cleaned, isDownloaded = true)
                        _activeChapters.value = updated
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun setChapterIndex(index: Int) {
        if (index in _activeChapters.value.indices) {
            val prevIndex = _currentChapterIndex.value
            _currentChapterIndex.value = index
            val novel = _activeNovel.value ?: return
            viewModelScope.launch {
                repository.updateProgress(novel.originalUrl, index, 0)
                ensureChapterLoaded(index)
                if (index > prevIndex) {
                    repository.incrementCompletedChapter(novel.originalUrl)
                    _readingStats.value = repository.getReadingStats(novel.originalUrl)
                }
            }
        }
    }

    fun commitCurrentSession() {
        if (sessionStartTime > 0L) {
            val elapsedMs = System.currentTimeMillis() - sessionStartTime
            sessionStartTime = System.currentTimeMillis()
            val minutes = (elapsedMs / 60000L).coerceAtLeast(if (elapsedMs >= 30000L) 1L else 0L)
            val novel = _activeNovel.value
            if (novel != null && minutes > 0L) {
                viewModelScope.launch {
                    repository.recordReadingSession(novel.originalUrl, minutes)
                    _readingStats.value = repository.getReadingStats(novel.originalUrl)
                }
            }
        }
    }

    fun updateProgress(pos: Int) {
        val novel = _activeNovel.value ?: return
        viewModelScope.launch {
            repository.updateProgress(novel.originalUrl, _currentChapterIndex.value, pos)
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        preferences.readerFontSize = size
    }

    fun setLineHeight(height: Float) {
        _lineHeight.value = height
        preferences.readerLineHeight = height
    }

    fun setHorizontalPadding(padding: Int) {
        _horizontalPadding.value = padding
        preferences.readerHorizontalPadding = padding
    }

    fun setVerticalPadding(padding: Int) {
        _verticalPadding.value = padding
        preferences.readerVerticalPadding = padding
    }

    fun setThemeMode(mode: ReaderThemeMode) {
        _themeMode.value = mode
        preferences.readerThemeMode = mode.name
    }

    fun setFontMode(mode: ReaderFontMode) {
        _fontMode.value = mode
        preferences.readerFontMode = mode.name
    }

    fun addBookmark(snippet: String, scrollPos: Int) {
        val novel = _activeNovel.value ?: return
        val currentChapter = _activeChapters.value.getOrNull(_currentChapterIndex.value) ?: return
        viewModelScope.launch {
            repository.addBookmark(
                novelUrl = novel.originalUrl,
                chapterIndex = _currentChapterIndex.value,
                title = currentChapter.title,
                snippet = snippet,
                pos = scrollPos
            )
            loadBookmarks(novel.originalUrl)
        }
    }

    fun deleteBookmark(id: Long) {
        val novel = _activeNovel.value ?: return
        viewModelScope.launch {
            repository.deleteBookmark(id)
            loadBookmarks(novel.originalUrl)
        }
    }

    private fun loadBookmarks(novelUrl: String) {
        viewModelScope.launch {
            repository.getBookmarks(novelUrl).collect {
                _bookmarks.value = it
            }
        }
    }

    // Commandes TTS
    fun toggleTtsBar() {
        _isTtsBarVisible.value = !_isTtsBarVisible.value
    }

    fun showTtsBar() {
        _isTtsBarVisible.value = true
    }

    fun hideTtsBar() {
        _isTtsBarVisible.value = false
        ttsPlayer.stop()
    }

    fun startTtsForCurrentChapter(paragraphs: List<String>, startParagraph: Int = 0) {
        val currentChapter = _activeChapters.value.getOrNull(_currentChapterIndex.value) ?: return
        _isTtsBarVisible.value = true
        ttsPlayer.startChapterReading(
            chapterUrl = currentChapter.url,
            paragraphs = paragraphs,
            startParagraphIndex = startParagraph
        )
    }

    fun playPauseTts() {
        when (ttsPlayState.value) {
            TtsPlayState.PLAYING -> ttsPlayer.pause()
            TtsPlayState.PAUSED -> ttsPlayer.resume()
            else -> {
                // Relancer si stoppé
                val currentChapter = _activeChapters.value.getOrNull(_currentChapterIndex.value) ?: return
                val raw = currentChapter.content
                val paragraphs = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.formatToParagraphs(raw, _activeNovel.value?.title)
                startTtsForCurrentChapter(paragraphs, 0)
            }
        }
    }

    fun seekTts(ms: Long) {
        ttsPlayer.seekTo(ms)
    }

    fun skipBackwardTts() {
        ttsPlayer.skipBackward10s()
    }

    fun skipForwardTts() {
        ttsPlayer.skipForward10s()
    }

    fun setTtsSpeed(speed: Float) {
        ttsPlayer.setSpeed(speed)
    }

    fun setTtsVoice(voice: VoiceOption) {
        ttsPlayer.setVoice(voice)
    }

    override fun onCleared() {
        super.onCleared()
        commitCurrentSession()
        ttsPlayer.destroy()
    }
}

