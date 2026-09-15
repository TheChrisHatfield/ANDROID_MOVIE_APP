package com.torrentmovie.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.torrentmovie.app.ui.fold.FoldDeviceProfile
import com.torrentmovie.app.ui.fold.FoldSearchDetailLayout
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.torrentmovie.app.ui.detail.TorrentDetailScreen
import com.torrentmovie.app.ui.help.HelpScreen
import com.torrentmovie.app.ui.search.SearchScreen
import com.torrentmovie.app.ui.search.SearchViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torrentmovie.app.ui.settings.SettingsScreen
import com.torrentmovie.app.ui.uploaded.UploadedScreen
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.PendingFoldDetail

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
    val context = LocalContext.current
    val foldTwoPaneDevice = remember {
        FoldDeviceProfile.twoPaneSearchDetailEnabled(context)
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useFoldTwoPane = foldTwoPaneDevice &&
        screenWidthDp >= FoldDeviceProfile.TWO_PANE_MIN_WIDTH_DP

    LaunchedEffect(useFoldTwoPane) {
        if (useFoldTwoPane) {
            val entry = navController.currentBackStackEntry
            val route = entry?.destination?.route
            if (route != null && route.startsWith("detail/")) {
                container.pendingFoldDetail = PendingFoldDetail(
                    resultId = entry.arguments?.getString("resultId") ?: return@LaunchedEffect,
                    name = entry.arguments?.getString("name") ?: "",
                    site = entry.arguments?.getString("site") ?: "",
                )
                navController.popBackStack(Routes.SEARCH, inclusive = false)
            }
        } else {
            val pending = container.pendingFoldNarrowDetail
            if (pending != null) {
                container.pendingFoldNarrowDetail = null
                val cached = container.searchResultStore.get(pending.resultId)
                val name = cached?.name?.takeIf { it.isNotBlank() } ?: pending.name
                val site = cached?.site?.takeIf { it.isNotBlank() } ?: pending.site
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute?.startsWith("detail/") != true) {
                    navController.navigate(Routes.detail(pending.resultId, name, site)) {
                        launchSingleTop = true
                        popUpTo(Routes.SEARCH) { inclusive = false }
                    }
                }
            }
        }
    }

    NavHost(navController, startDestination = Routes.SEARCH, modifier = modifier) {
        composable(Routes.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel { SearchViewModel(container) }
            if (useFoldTwoPane) {
                FoldSearchDetailLayout(container, searchViewModel)
            } else {
                SearchScreen(
                    container = container,
                    sharedViewModel = searchViewModel,
                    onOpenDetail = { r ->
                    container.searchResultStore.put(r)
                    navController.navigate(Routes.detail(r.id, r.name, r.site)) {
                        launchSingleTop = true
                        popUpTo(Routes.SEARCH) { inclusive = false }
                    }
                },
                )
            }
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
