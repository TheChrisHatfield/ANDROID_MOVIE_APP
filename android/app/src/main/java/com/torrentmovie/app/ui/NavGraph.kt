package com.torrentmovie.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.torrentmovie.app.ui.detail.TorrentDetailScreen
import com.torrentmovie.app.ui.help.HelpScreen
import com.torrentmovie.app.ui.search.SearchScreen
import com.torrentmovie.app.ui.settings.SettingsScreen
import com.torrentmovie.app.ui.uploaded.UploadedScreen
import com.torrentmovie.core.data.AppContainer

object Routes {
    const val SEARCH = "search"
    const val DETAIL = "detail/{resultId}?name={name}&site={site}"
    const val UPLOADED = "uploaded"
    const val SETTINGS = "settings"
    const val HELP = "help"

    fun detail(resultId: String, name: String, site: String): String {
        return "detail/$resultId?name=${Uri.encode(name)}&site=${Uri.encode(site)}"
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(navController, startDestination = Routes.SEARCH, modifier = modifier) {
        composable(Routes.SEARCH) {
            SearchScreen(container, onOpenDetail = { r ->
                container.searchResultStore.put(r)
                navController.navigate(Routes.detail(r.id, r.name, r.site)) {
                    launchSingleTop = true
                    popUpTo(Routes.SEARCH) { inclusive = false }
                }
            })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("resultId") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("site") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val resultId = entry.arguments?.getString("resultId") ?: ""
            val navName = entry.arguments?.getString("name") ?: ""
            val navSite = entry.arguments?.getString("site") ?: ""
            val cached = remember(resultId) { container.searchResultStore.get(resultId) }
            TorrentDetailScreen(
                container = container,
                resultId = resultId,
                name = cached?.name ?: navName,
                site = cached?.site ?: navSite,
                initialMagnet = cached?.magnet,
            )
        }
        composable(Routes.UPLOADED) { UploadedScreen(container) }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
        composable(Routes.HELP) { HelpScreen() }
    }
}
