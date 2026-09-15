package com.torrentmovie.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.torrentmovie.core.data.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(container: AppContainer) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.SEARCH
    var settings by remember { mutableStateOf(container.settingsRepository.load()) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()

    LaunchedEffect(route, settingsRevision) {
        settings = container.settingsRepository.load()
    }

    if (!settings.disclaimerAccepted) {
        DisclaimerDialog(
            onAccept = {
                val updated = settings.copy(disclaimerAccepted = true)
                if (container.settingsRepository.save(updated)) {
                    settings = updated
                } else {
                    Toast.makeText(context, "Failed to save — try again", Toast.LENGTH_SHORT).show()
                }
            },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Torrent Movies", modifier = Modifier.padding(16.dp))
                NavigationDrawerItem(
                    label = { Text("Search") },
                    selected = route.startsWith("search"),
                    onClick = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Uploaded") },
                    selected = route == Routes.UPLOADED,
                    onClick = {
                        navController.navigate(Routes.UPLOADED) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = route == Routes.SETTINGS,
                    onClick = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Help") },
                    selected = route == Routes.HELP,
                    onClick = {
                        navController.navigate(Routes.HELP) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Torrent Movies") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = { SeedboxStatusChip(container) },
                )
            },
        ) { padding ->
            AppNavGraph(
                navController = navController,
                container = container,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
