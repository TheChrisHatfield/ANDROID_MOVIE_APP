package com.torrentmovie.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationRailItemColors
import com.torrentmovie.app.ui.theme.Cream
import com.torrentmovie.app.ui.theme.PantoneRed
import com.torrentmovie.app.ui.theme.SurfaceWhite
import com.torrentmovie.app.ui.theme.TextCharcoal
import androidx.navigation.NavHostController

internal data class AppDestination(
    val route: String,
    val label: String,
    val selected: (String, Boolean) -> Boolean,
)

private val appDestinations = listOf(
    AppDestination(
        route = Routes.SEARCH,
        label = "Search",
        selected = { route, onDetail -> route.startsWith("search") || onDetail },
    ),
    AppDestination(
        route = Routes.UPLOADED,
        label = "Uploaded",
        selected = { route, _ -> route == Routes.UPLOADED },
    ),
    AppDestination(
        route = Routes.SETTINGS,
        label = "Settings",
        selected = { route, _ -> route == Routes.SETTINGS },
    ),
    AppDestination(
        route = Routes.HELP,
        label = "Help",
        selected = { route, _ -> route == Routes.HELP },
    ),
)

@Composable
private fun missyNavigationItemColors(): NavigationBarItemColors {
    return NavigationBarItemDefaults.colors(
        selectedIconColor = PantoneRed,
        selectedTextColor = PantoneRed,
        unselectedIconColor = TextCharcoal,
        unselectedTextColor = TextCharcoal,
        indicatorColor = SurfaceWhite,
    )
}

@Composable
private fun missyNavigationRailItemColors(): NavigationRailItemColors {
    return NavigationRailItemDefaults.colors(
        selectedIconColor = PantoneRed,
        selectedTextColor = PantoneRed,
        unselectedIconColor = TextCharcoal,
        unselectedTextColor = TextCharcoal,
        indicatorColor = SurfaceWhite,
    )
}

private fun destinationIcon(label: String) = when (label) {
    "Search" -> Icons.Default.Search
    "Uploaded" -> Icons.Default.CloudUpload
    "Settings" -> Icons.Default.Settings
    else -> Icons.AutoMirrored.Filled.Help
}

@Composable
internal fun AppNavigationRail(
    route: String,
    onDetailRoute: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.padding(vertical = 8.dp),
        containerColor = Cream,
    ) {
        appDestinations.forEach { dest ->
            NavigationRailItem(
                icon = { Icon(destinationIcon(dest.label), contentDescription = dest.label) },
                label = { Text(dest.label) },
                selected = dest.selected(route, onDetailRoute),
                onClick = { onNavigate(dest.route) },
                colors = missyNavigationRailItemColors(),
            )
        }
    }
}

@Composable
internal fun AppNavigationBar(
    route: String,
    onDetailRoute: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Cream,
        tonalElevation = 0.dp,
    ) {
        appDestinations.forEach { dest ->
            NavigationBarItem(
                icon = { Icon(destinationIcon(dest.label), contentDescription = dest.label) },
                label = { Text(dest.label) },
                selected = dest.selected(route, onDetailRoute),
                onClick = { onNavigate(dest.route) },
                colors = missyNavigationItemColors(),
            )
        }
    }
}

internal fun navigateTo(
    navController: NavHostController,
    destinationRoute: String,
) {
    navController.navigate(destinationRoute) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
