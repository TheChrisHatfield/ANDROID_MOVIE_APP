package com.torrentmovie.app.ui

import androidx.compose.runtime.Composable
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
    const val DETAIL = "detail/{resultId}/{name}/{site}/{magnet}"
    const val UPLOADED = "uploaded"
    const val SETTINGS = "settings"
    const val HELP = "help"

    fun detail(resultId: String, name: String, site: String, magnet: String) =
        "detail/$resultId/${name.encode()}/${site.encode()}/${magnet.encode()}"

    private fun String.encode(): String = java.net.URLEncoder.encode(this, "UTF-8")
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
                navController.navigate(
                    Routes.detail(r.id, r.name, r.site, r.magnet ?: ""),
                )
            })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("resultId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
                navArgument("site") { type = NavType.StringType },
                navArgument("magnet") { type = NavType.StringType },
            ),
        ) { entry ->
            TorrentDetailScreen(
                container = container,
                resultId = entry.arguments?.getString("resultId") ?: "",
                name = entry.arguments?.getString("name")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "",
                site = entry.arguments?.getString("site")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "",
                initialMagnet = entry.arguments?.getString("magnet")?.let { java.net.URLDecoder.decode(it, "UTF-8") },
            )
        }
        composable(Routes.UPLOADED) { UploadedScreen(container) }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
        composable(Routes.HELP) { HelpScreen() }
    }
}
