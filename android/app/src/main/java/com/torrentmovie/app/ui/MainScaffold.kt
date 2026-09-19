package com.torrentmovie.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.torrentmovie.app.ui.adaptive.AdaptiveLayout
import com.torrentmovie.app.ui.theme.AppBranding
import com.torrentmovie.core.data.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(container: AppContainer) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.SEARCH
    val onDetailRoute = route.startsWith("detail/")
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useNavigationRail = AdaptiveLayout.useNavigationRail(screenWidthDp)
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

    fun navigate(destinationRoute: String) {
        navigateTo(navController, destinationRoute)
    }

    val mainContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(AppBranding.DISPLAY_NAME) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    navigationIcon = {
                        if (onDetailRoute && !useNavigationRail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        SeedboxStatusChip(
                            container = container,
                            onOpenSettings = { navigate(Routes.SETTINGS) },
                        )
                    },
                )
            },
            bottomBar = {
                if (!useNavigationRail && !onDetailRoute) {
                    AppNavigationBar(
                        route = route,
                        onDetailRoute = onDetailRoute,
                        onNavigate = ::navigate,
                    )
                }
            },
        ) { padding ->
            AppNavGraph(
                navController = navController,
                container = container,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (useNavigationRail) {
        Row(Modifier.fillMaxSize()) {
            AppNavigationRail(
                route = route,
                onDetailRoute = onDetailRoute,
                onNavigate = ::navigate,
            )
            mainContent(Modifier.weight(1f))
        }
    } else {
        mainContent(Modifier.fillMaxSize())
    }
}
