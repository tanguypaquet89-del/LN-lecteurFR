package com.nahrahviing.lecteurnovel.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.ui.components.DownloadQueueDialog
import com.nahrahviing.lecteurnovel.ui.components.ExportNovelDialog
import com.nahrahviing.lecteurnovel.ui.viewmodel.LibrarySortType
import com.nahrahviing.lecteurnovel.ui.viewmodel.LibraryViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.ReadingStatusFilter
import com.nahrahviing.lecteurnovel.util.DownloadStatus
import com.nahrahviing.lecteurnovel.util.DownloadTask
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToBrowser: () -> Unit,
    onOpenReader: (SavedNovelEntity) -> Unit
) {
    val savedNovels by viewModel.savedNovels.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val customCategories by viewModel.customCategories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val queueTasks by viewModel.queueTasks.collectAsState()
    val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var novelToEditCategory by remember { mutableStateOf<SavedNovelEntity?>(null) }
    var novelToExport by remember { mutableStateOf<SavedNovelEntity?>(null) }
    var novelToDeleteFiles by remember { mutableStateOf<SavedNovelEntity?>(null) }
    var novelToDeleteAll by remember { mutableStateOf<SavedNovelEntity?>(null) }

    // Liste consolidée de toutes les catégories
    val allCategories = remember(savedNovels, customCategories) {
        val novelCategories = savedNovels.map { it.category }.filter { it.isNotBlank() && it != "Tous" }
        val combined = (listOf("Tous") + customCategories + novelCategories).distinct()
        combined
    }

    // Filtrage et Tri avancés
    val filteredNovels = remember(savedNovels, selectedCategory, searchQuery, sortType, sortAscending, statusFilter) {
        var list = savedNovels

        // 1. Recherche par titre, auteur ou genre
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            list = list.filter {
                it.title.lowercase(Locale.getDefault()).contains(q) ||
                it.author.lowercase(Locale.getDefault()).contains(q) ||
                it.genres.lowercase(Locale.getDefault()).contains(q)
            }
        }

        // 2. Filtre par Catégorie
        if (selectedCategory != "Tous") {
            list = list.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        // 3. Filtre par Statut de lecture
        list = when (statusFilter) {
            ReadingStatusFilter.ALL -> list
            ReadingStatusFilter.IN_PROGRESS -> list.filter {
                it.lastReadChapterIndex > 0 && it.lastReadChapterIndex < it.totalChapters - 1
            }
            ReadingStatusFilter.UNREAD -> list.filter {
                it.lastReadChapterIndex == 0
            }
            ReadingStatusFilter.COMPLETED -> list.filter {
                it.totalChapters > 0 && it.lastReadChapterIndex >= it.totalChapters - 1
            }
            ReadingStatusFilter.FAVORITES -> list.filter { it.isFavorite }
        }

        // 4. Tri
        val sorted = when (sortType) {
            LibrarySortType.LAST_READ -> {
                if (sortAscending) list.sortedBy { it.lastReadAt }
                else list.sortedByDescending { it.lastReadAt }
            }
            LibrarySortType.DATE_ADDED -> {
                if (sortAscending) list.sortedBy { it.addedAt }
                else list.sortedByDescending { it.addedAt }
            }
            LibrarySortType.ALPHABETICAL -> {
                if (sortAscending) list.sortedBy { it.title.lowercase(Locale.getDefault()) }
                else list.sortedByDescending { it.title.lowercase(Locale.getDefault()) }
            }
            LibrarySortType.TOTAL_CHAPTERS -> {
                if (sortAscending) list.sortedBy { it.totalChapters }
                else list.sortedByDescending { it.totalChapters }
            }
        }
        sorted
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f Mo", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f Ko", bytes / 1024.0)
            else -> "$bytes octets"
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                if (isSearchActive) {
                    // Barre de recherche active
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer recherche")
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Rechercher un roman, auteur, genre...") },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Effacer")
                                    }
                                }
                            }
                        )
                    }
                } else {
                    // TopAppBar standard
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    "Ma Bibliothèque",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    "${filteredNovels.size} roman(s)" +
                                        if (filteredNovels.size != savedNovels.size) " sur ${savedNovels.size}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Rechercher")
                            }
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Trier et filtrer")
                            }
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
                                        tint = if (activeDownloadsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                if (savedNovels.isNotEmpty()) {
                    // Ligne 1 : Onglets Catégories / Tags personnalisés
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        allCategories.forEach { category ->
                            val isSelected = category == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSelectedCategory(category) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            category,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                        if (category != "Tous" && customCategories.contains(category)) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Supprimer la catégorie",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { categoryToDelete = category }
                                            )
                                        }
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }

                        // Bouton d'ajout de catégorie personnalisée
                        AssistChip(
                            onClick = { showAddCategoryDialog = true },
                            label = { Text("+ Nouvelle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }

                    // Ligne 2 : Statuts de lecture & Bouton de tri rapide
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Statuts de lecture
                        ReadingStatusFilter.values().forEach { filter ->
                            val isSelected = statusFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setStatusFilter(filter) },
                                label = { Text(filter.label, fontSize = 11.sp) },
                                leadingIcon = {
                                    val icon = when (filter) {
                                        ReadingStatusFilter.ALL -> Icons.Default.AllInclusive
                                        ReadingStatusFilter.IN_PROGRESS -> Icons.Default.MenuBook
                                        ReadingStatusFilter.UNREAD -> Icons.Default.BookmarkBorder
                                        ReadingStatusFilter.COMPLETED -> Icons.Default.CheckCircle
                                        ReadingStatusFilter.FAVORITES -> Icons.Default.Favorite
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (filter == ReadingStatusFilter.FAVORITES && isSelected) MaterialTheme.colorScheme.error else LocalContentColor.current
                                    )
                                }
                            )
                        }

                        // Bouton de tri rapide avec direction
                        AssistChip(
                            onClick = { showSortSheet = true },
                            label = {
                                Text(
                                    "${sortType.label} ${if (sortAscending) "↑" else "↓"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(13.dp))
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    ) { paddingValues ->
        if (savedNovels.isEmpty()) {
            // Bibliothèque totalement vide
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(68.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Aucun roman dans votre bibliothèque",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Parcourez vos sites favoris dans le navigateur pour ajouter des romans ou les télécharger hors-ligne.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onNavigateToBrowser) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvrir le navigateur")
                    }
                }
            }
        } else if (filteredNovels.isEmpty()) {
            // Aucun résultat pour la recherche / filtre actuel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.FilterAltOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Aucun roman ne correspond à vos filtres",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Essayez de modifier votre terme de recherche ou de réinitialiser la catégorie et le statut.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        viewModel.setSearchQuery("")
                        viewModel.setSelectedCategory("Tous")
                        viewModel.setStatusFilter(ReadingStatusFilter.ALL)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Réinitialiser les filtres")
                    }
                }
            }
        } else {
            // Grille de romans
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val isTablet = maxWidth >= 720.dp
                val is14InchTablet = maxWidth >= 1100.dp

                val minCellSize = when {
                    is14InchTablet -> 210.dp
                    isTablet -> 180.dp
                    else -> 150.dp
                }
                val paddingHorizontal = when {
                    is14InchTablet -> 32.dp
                    isTablet -> 24.dp
                    else -> 16.dp
                }
                val spacing = when {
                    is14InchTablet -> 20.dp
                    else -> 16.dp
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minCellSize),
                    contentPadding = PaddingValues(horizontal = paddingHorizontal, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNovels, key = { it.originalUrl }) { novel ->
                        val activeTask = remember(queueTasks, novel.originalUrl) {
                            queueTasks.find {
                                it.novelUrl == novel.originalUrl &&
                                (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
                            }
                        }

                        NovelCard(
                            novel = novel,
                            activeTask = activeTask,
                            onClick = { onOpenReader(novel) },
                            onFavoriteToggle = { viewModel.toggleFavorite(novel) },
                            onChangeCategory = { novelToEditCategory = novel },
                            onExport = { novelToExport = novel },
                            onDeleteFilesOnly = { novelToDeleteFiles = novel },
                            onDeleteNovelAndFiles = { novelToDeleteAll = novel },
                            onRedownload = { viewModel.redownloadNovel(novel) }
                        )
                    }
                }
            }
        }
    }

    // Dialog Création Catégorie / Tag
    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        val quickTags = listOf("Isekai", "Fantasy", "Action", "Romance", "En pause", "Chef-d'œuvre")

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            icon = { Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Nouvelle catégorie / Tag") },
            text = {
                Column {
                    Text(
                        "Créez une catégorie pour classer vos romans :",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Nom de la catégorie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Suggestions rapides :", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickTags.forEach { tag ->
                            SuggestionChip(
                                onClick = { newCatName = tag },
                                label = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            viewModel.addCustomCategory(newCatName)
                            showAddCategoryDialog = false
                        }
                    },
                    enabled = newCatName.isNotBlank()
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Confirmation suppression de catégorie personnalisée
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer la catégorie « $cat » ?") },
            text = {
                Text("Cette catégorie sera retirée de vos filtres. Les romans associés conserveront leur catégorie jusqu'à modification.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeCustomCategory(cat)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialog Modifier la catégorie d'un roman
    novelToEditCategory?.let { novel ->
        var selectedCat by remember { mutableStateOf(novel.category) }
        var isCustomInput by remember { mutableStateOf(false) }
        var customInputText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { novelToEditCategory = null },
            icon = { Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Classer « ${novel.title} »") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Sélectionnez la catégorie ou le tag pour ce roman :",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val options = remember(allCategories) {
                        (allCategories.filter { it != "Tous" } + listOf("Général")).distinct()
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        options.forEach { opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCat = opt
                                        isCustomInput = false
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = !isCustomInput && selectedCat == opt,
                                    onClick = {
                                        selectedCat = opt
                                        isCustomInput = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(opt, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCustomInput = true }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isCustomInput,
                                onClick = { isCustomInput = true }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Autre catégorie...", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (isCustomInput) {
                            OutlinedTextField(
                                value = customInputText,
                                onValueChange = { customInputText = it },
                                label = { Text("Nouvelle catégorie") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalCat = if (isCustomInput) customInputText.trim() else selectedCat
                        if (finalCat.isNotBlank()) {
                            viewModel.updateNovelCategory(novel, finalCat)
                            if (isCustomInput) {
                                viewModel.addCustomCategory(finalCat)
                            }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("✓ Catégorie mise à jour : $finalCat")
                            }
                        }
                        novelToEditCategory = null
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToEditCategory = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialog File d'attente
    if (showQueueDialog) {
        DownloadQueueDialog(
            onDismiss = { showQueueDialog = false }
        )
    }

    // Bottom Sheet ou Dialog de Tri Avancé
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trier la bibliothèque",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.toggleSortDirection() }) {
                        Icon(
                            if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (sortAscending) "Croissant" else "Décroissant")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                LibrarySortType.values().forEach { sort ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSortType(sort)
                                showSortSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sortType == sort,
                            onClick = {
                                viewModel.setSortType(sort)
                                showSortSheet = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(sort.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    // Dialog Export de roman (EPUB, PDF, ODT, TXT)
    novelToExport?.let { novel ->
        ExportNovelDialog(
            novel = novel,
            repository = viewModel.repository,
            onDismiss = { novelToExport = null },
            onMessage = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }

    // Confirmation suppression fichiers seulement
    novelToDeleteFiles?.let { novel ->
        AlertDialog(
            onDismissRequest = { novelToDeleteFiles = null },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Supprimer les fichiers locaux ?") },
            text = {
                Text(
                    "Cette action supprime les fichiers EPUB, PDF, ODT, TXT et chapitres stockés pour « ${novel.title} » afin de libérer l'espace disque de l'appareil. Le roman restera présent dans votre bibliothèque en mode lecture en ligne."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentNovel = novel
                        novelToDeleteFiles = null
                        viewModel.deleteOnlyNovelFiles(currentNovel) { freed ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("✓ Fichiers supprimés (${formatBytes(freed)} libérés)")
                            }
                        }
                    }
                ) {
                    Text("Supprimer les fichiers")
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToDeleteFiles = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Confirmation suppression complète
    novelToDeleteAll?.let { novel ->
        AlertDialog(
            onDismissRequest = { novelToDeleteAll = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer de la bibliothèque ?") },
            text = {
                Text("Voulez-vous retirer définitivement « ${novel.title} » de votre bibliothèque et supprimer tous ses fichiers téléchargés ?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentNovel = novel
                        novelToDeleteAll = null
                        viewModel.deleteNovelAndFiles(currentNovel) { freed ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("✓ Roman et fichiers supprimés (${formatBytes(freed)} libérés)")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Tout supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToDeleteAll = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun NovelCard(
    novel: SavedNovelEntity,
    activeTask: DownloadTask?,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onChangeCategory: () -> Unit,
    onExport: () -> Unit,
    onDeleteFilesOnly: () -> Unit,
    onDeleteNovelAndFiles: () -> Unit,
    onRedownload: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    // Détermination de l'état de téléchargement / disponibilité
    val isDownloading = activeTask != null &&
        (activeTask.status == DownloadStatus.DOWNLOADING || activeTask.status == DownloadStatus.QUEUED)

    val isOfflineAvailable = remember(novel.epubFilePath, novel.exportMode) {
        if (novel.exportMode == "ONLINE_STREAM") {
            false
        } else if (novel.epubFilePath != null) {
            val f = File(novel.epubFilePath)
            f.exists() && f.length() > 0
        } else {
            novel.exportMode != "ONLINE_STREAM"
        }
    }

    val readProgressRatio = remember(novel.lastReadChapterIndex, novel.totalChapters) {
        if (novel.totalChapters > 0) {
            ((novel.lastReadChapterIndex + 1).toFloat() / novel.totalChapters).coerceIn(0f, 1f)
        } else 0f
    }

    val isCompleted = novel.totalChapters > 0 && novel.lastReadChapterIndex >= novel.totalChapters - 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            ) {
                // Couverture
                if (novel.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = novel.coverUrl,
                        contentDescription = novel.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. BADGE DE STATUT VISUEL (Téléchargé, En ligne, ou En cours de téléchargement)
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = when {
                        isDownloading -> Color(0xFFFF9800).copy(alpha = 0.92f) // Ambre / Orange
                        isOfflineAvailable -> Color(0xFF2E7D32).copy(alpha = 0.92f) // Vert émeraude
                        else -> Color(0xFF1565C0).copy(alpha = 0.90f) // Bleu Indigo (En ligne)
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isDownloading) {
                            if (activeTask?.status == DownloadStatus.QUEUED) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Text("En file...", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Text("${((activeTask?.progress ?: 0f) * 100).toInt()}%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isOfflineAvailable) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                            Text("Hors-ligne", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                            Text("En ligne", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Bouton favori
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (novel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (novel.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. BADGE DE PROGRESSION DE LECTURE (En bas à droite de la couverture)
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(11.dp))
                            Text("Terminé", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                "Ch. ${novel.lastReadChapterIndex + 1}/${novel.totalChapters}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Fine jauge de lecture au bas de la jaquette
                if (readProgressRatio > 0f) {
                    LinearProgressIndicator(
                        progress = { readProgressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f)
                    )
                }
            }

            // Informations textuelles et métadonnées
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Menu dropdown options
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Options du roman",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Lire le roman") },
                                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Changer de catégorie / Tag") },
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onChangeCategory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exporter (EPUB, PDF, ODT, TXT)...") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isOfflineAvailable) "Re-télécharger" else "Télécharger hors-ligne") },
                                leadingIcon = { 
                                    Icon(
                                        if (isOfflineAvailable) Icons.Default.Refresh else Icons.Default.Download, 
                                        contentDescription = null 
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    onRedownload()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Supprimer les fichiers locaux") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDeleteFilesOnly()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Supprimer de la bibliothèque",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteNovelAndFiles()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Étiquette de Catégorie cliquable
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onChangeCategory() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Label,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = novel.category.ifBlank { "Général" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${novel.totalChapters} chap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (novel.exportMode == "SEPARATE_CHAPTERS") "Fichiers séparés"
                               else if (novel.exportMode == "ONLINE_STREAM") "Streaming"
                               else "Doc unique",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
