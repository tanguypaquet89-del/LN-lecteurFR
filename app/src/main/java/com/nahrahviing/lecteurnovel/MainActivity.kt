package com.nahrahviing.lecteurnovel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nahrahviing.lecteurnovel.ui.screens.browser.BrowserScreen
import com.nahrahviing.lecteurnovel.ui.screens.library.LibraryScreen
import com.nahrahviing.lecteurnovel.ui.screens.reader.ReaderScreen
import com.nahrahviing.lecteurnovel.ui.screens.settings.SettingsScreen
import com.nahrahviing.lecteurnovel.ui.screens.profile.ProfileScreen
import com.nahrahviing.lecteurnovel.ui.theme.ChiReadsTheme
import com.nahrahviing.lecteurnovel.ui.viewmodel.BrowserViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.LibraryViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.ReaderViewModel
import com.nahrahviing.lecteurnovel.ui.viewmodel.SettingsViewModel

import dagger.hilt.android.AndroidEntryPoint

/**
 * Activité principale de l'application LecteurNovel.
 * Point d'entrée Android configuré avec Hilt pour l'injection de dépendances
 * et Jetpack Compose en mode bord à bord (edge-to-edge).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChiReadsTheme {
                MainApp()
            }
        }
    }
}

/**
 * Composant racine gérant la navigation, les permissions système et l'adaptation responsive.
 * - Sur smartphone / tablette en mode portrait : barre de navigation inférieure standard (NavigationBar)
 * - Sur grand écran en mode paysage (largeur >= 840dp) : barre latérale ergonomique (NavigationRail)
 * - Masquage automatique des barres de navigation lors de la lecture immersive plein écran (ReaderScreen).
 */
@Composable
fun MainApp() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* permission handled */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "library"

    val browserViewModel: BrowserViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val readerViewModel: ReaderViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        // Activer le rail latéral uniquement en mode Paysage large (largeur >= 840dp)
        val useNavigationRail = isLandscape && maxWidth >= 840.dp
        val is14InchTablet = maxWidth >= 1100.dp

        if (useNavigationRail) {
            // Disposition grand écran en mode PAYSAGE
            Row(modifier = Modifier.fillMaxSize()) {
                if (currentRoute != "reader") {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(top = 12.dp, bottom = 16.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Book,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NavigationRailItem(
                                selected = currentRoute == "browser",
                                onClick = { navController.navigate("browser") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Language, contentDescription = "Navigateur") },
                                label = { Text("Navigateur") },
                                alwaysShowLabel = true
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            NavigationRailItem(
                                selected = currentRoute == "library",
                                onClick = { navController.navigate("library") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Book, contentDescription = "Bibliothèque") },
                                label = { Text("Bibliothèque") },
                                alwaysShowLabel = true
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            NavigationRailItem(
                                selected = currentRoute == "profile",
                                onClick = { navController.navigate("profile") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                                label = { Text("Profil") },
                                alwaysShowLabel = true
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            NavigationRailItem(
                                selected = currentRoute == "settings",
                                onClick = { navController.navigate("settings") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Paramètres") },
                                label = { Text("Paramètres") },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AppNavHost(
                        navController = navController,
                        browserViewModel = browserViewModel,
                        libraryViewModel = libraryViewModel,
                        readerViewModel = readerViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        } else {
            // Disposition en mode PORTRAIT (Smartphone et Tablettes jusqu'à 14" tenues verticalement)
            Scaffold(
                bottomBar = {
                    if (currentRoute != "reader") {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == "browser",
                                onClick = { navController.navigate("browser") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Language, contentDescription = "Navigateur") },
                                label = { Text("Navigateur") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "library",
                                onClick = { navController.navigate("library") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Book, contentDescription = "Bibliothèque") },
                                label = { Text("Bibliothèque") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "profile",
                                onClick = { navController.navigate("profile") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                                label = { Text("Profil") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "settings",
                                onClick = { navController.navigate("settings") { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Paramètres") },
                                label = { Text("Paramètres") }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AppNavHost(
                        navController = navController,
                        browserViewModel = browserViewModel,
                        libraryViewModel = libraryViewModel,
                        readerViewModel = readerViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    browserViewModel: BrowserViewModel,
    libraryViewModel: LibraryViewModel,
    readerViewModel: ReaderViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(navController = navController, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(
                viewModel = browserViewModel,
                onNavigateToLibrary = {
                    navController.navigate("library") { launchSingleTop = true; restoreState = true }
                }
            )
        }
        composable("library") {
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateToBrowser = {
                    navController.navigate("browser") { launchSingleTop = true; restoreState = true }
                },
                onOpenReader = { novel ->
                    readerViewModel.openReader(novel)
                    navController.navigate("reader")
                }
            )
        }
        composable("reader") {
            ReaderScreen(
                viewModel = readerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                viewModel = readerViewModel,
                libraryViewModel = libraryViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToSettings = {
                    navController.navigate("settings") { launchSingleTop = true; restoreState = true }
                }
            )
        }
        composable("settings") {
            SettingsScreen(viewModel = settingsViewModel)
        }
    }
}
