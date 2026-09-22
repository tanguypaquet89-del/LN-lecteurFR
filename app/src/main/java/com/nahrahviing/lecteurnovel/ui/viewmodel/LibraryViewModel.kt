package com.nahrahviing.lecteurnovel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nahrahviing.lecteurnovel.util.DownloadQueueManager
import com.nahrahviing.lecteurnovel.util.DownloadTask
import com.nahrahviing.lecteurnovel.util.AppPreferences
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.model.Chapter

enum class LibrarySortType(val label: String) {
    LAST_READ("Dernière lecture"),
    DATE_ADDED("Date d'ajout"),
    ALPHABETICAL("Alphabétique (A-Z)"),
    TOTAL_CHAPTERS("Nombre de chapitres")
}

enum class ReadingStatusFilter(val label: String) {
    ALL("Tous"),
    IN_PROGRESS("En cours"),
    UNREAD("À lire"),
    COMPLETED("Terminé"),
    FAVORITES("Favoris")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    val repository: NovelRepository,
    private val application: Application
) : ViewModel() {

    private val prefs = AppPreferences(application)

    val savedNovels: StateFlow<List<SavedNovelEntity>> = repository.getAllSavedNovels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReadingStats: StateFlow<List<com.nahrahviing.lecteurnovel.data.local.ReadingStatEntity>> = repository.getAllReadingStatsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloadsCount = com.nahrahviing.lecteurnovel.util.DownloadQueueManager.activeCount
    val queueTasks: StateFlow<List<DownloadTask>> = DownloadQueueManager.tasks

    private val _selectedCategory = MutableStateFlow("Tous")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _customCategories = MutableStateFlow(prefs.customCategories)
    val customCategories: StateFlow<Set<String>> = _customCategories.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortType = MutableStateFlow(
        try { LibrarySortType.valueOf(prefs.librarySortType) } catch (_: Exception) { LibrarySortType.LAST_READ }
    )
    val sortType: StateFlow<LibrarySortType> = _sortType.asStateFlow()

    private val _sortAscending = MutableStateFlow(prefs.librarySortAscending)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _statusFilter = MutableStateFlow(
        try { ReadingStatusFilter.valueOf(prefs.libraryStatusFilter) } catch (_: Exception) { ReadingStatusFilter.ALL }
    )
    val statusFilter: StateFlow<ReadingStatusFilter> = _statusFilter.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortType(type: LibrarySortType) {
        _sortType.value = type
        prefs.librarySortType = type.name
    }

    fun toggleSortDirection() {
        val newAsc = !_sortAscending.value
        _sortAscending.value = newAsc
        prefs.librarySortAscending = newAsc
    }

    fun setStatusFilter(filter: ReadingStatusFilter) {
        _statusFilter.value = filter
        prefs.libraryStatusFilter = filter.name
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun addCustomCategory(category: String) {
        val trimmed = category.trim()
        if (trimmed.isNotBlank() && trimmed != "Tous") {
            val updated = _customCategories.value.toMutableSet()
            updated.add(trimmed)
            _customCategories.value = updated
            prefs.customCategories = updated
            _selectedCategory.value = trimmed
        }
    }

    fun removeCustomCategory(category: String) {
        val updated = _customCategories.value.toMutableSet()
        updated.remove(category)
        _customCategories.value = updated
        prefs.customCategories = updated
        if (_selectedCategory.value == category) {
            _selectedCategory.value = "Tous"
        }
    }

    fun toggleFavorite(novel: SavedNovelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(novel.originalUrl, !novel.isFavorite)
        }
    }

    fun deleteNovel(novel: SavedNovelEntity) {
        viewModelScope.launch {
            repository.deleteNovel(novel.originalUrl)
        }
    }

    fun deleteNovelAndFiles(novel: SavedNovelEntity, onFreed: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val freed = repository.deleteNovelAndAllFiles(novel)
            onFreed(freed)
        }
    }

    fun deleteOnlyNovelFiles(novel: SavedNovelEntity, onFreed: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val freed = repository.deleteNovelFiles(novel)
            onFreed(freed)
        }
    }

    
    fun redownloadNovel(novel: SavedNovelEntity) {
        viewModelScope.launch {
            val chapters = repository.getChaptersForNovel(novel.originalUrl)
            val novelInfo = NovelInfo(
                title = novel.title,
                author = novel.author,
                coverUrl = novel.coverUrl,
                synopsis = novel.synopsis,
                genres = novel.genres.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                chapters = chapters.map { ch -> 
                    Chapter(
                        index = ch.chapterIndex,
                        title = ch.title,
                        url = ch.url,
                        content = ch.content
                    ) 
                },
                originalUrl = novel.originalUrl
            )
            DownloadQueueManager.enqueueDownload(
                context = application,
                novel = novelInfo,
                format = "EPUB",
                exportMode = if (novel.exportMode.isNotBlank()) novel.exportMode else "SINGLE_EPUB",
                startChap = 1,
                endChap = novelInfo.chapters.size
            )
        }
    }

    fun updateNovelCategory(novel: SavedNovelEntity, newCategory: String) {
        viewModelScope.launch {
            val updatedNovel = novel.copy(category = newCategory.trim().ifEmpty { "Général" })
            repository.updateCategory(novel.originalUrl, newCategory.trim().ifEmpty { "Général" })
        }
    }
}
