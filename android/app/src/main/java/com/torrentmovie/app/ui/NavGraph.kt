package com.torrentmovie.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.torrentmovie.core.network.TorrentResultDto

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
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel(container) }
    val searchState by searchViewModel.state.collectAsState()
    val foldTwoPaneDevice = remember {
        FoldDeviceProfile.twoPaneSearchDetailEnabled(context)
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useFoldTwoPane = foldTwoPaneDevice &&
        screenWidthDp >= FoldDeviceProfile.TWO_PANE_MIN_WIDTH_DP
    val currentRoute = navController.currentBackStackEntry?.destination?.route

    fun allReleases(): List<TorrentResultDto> {
        return searchState.results + searchState.groups.flatMap { it.releases }
    }

    fun rematchRelease(resultId: String, name: String, site: String): TorrentResultDto? {
        val releases = allReleases()
        releases.find { it.id == resultId }?.let { return it }
        if (name.isBlank()) return null
        return releases.find { release ->
            release.name.equals(name, ignoreCase = true) &&
                (site.isBlank() || release.site == site)
        }
    }

    fun restoreFoldSelectionOnPhone(requireSearchRoute: Boolean) {
        if (useFoldTwoPane) return
        val pending = container.foldActiveSelection ?: return
        if (requireSearchRoute && currentRoute != Routes.SEARCH) return
        container.foldActiveSelection = null
        val matched = rematchRelease(pending.resultId, pending.name, pending.site)
            ?: container.searchResultStore.get(pending.resultId)?.let { stored ->
                rematchRelease(stored.id, stored.name, stored.site)
            }
        if (matched == null) return
        container.searchResultStore.put(matched)
        navController.navigate(Routes.detail(matched.id, matched.name, matched.site)) {
            launchSingleTop = true
            popUpTo(Routes.SEARCH) { inclusive = false }
        }
    }

    LaunchedEffect(useFoldTwoPane, currentRoute, searchState.hasSearched, searchState.loading) {
        if (!searchState.loading && searchState.hasSearched) {
            restoreFoldSelectionOnPhone(requireSearchRoute = true)
        }
    }

    LaunchedEffect(useFoldTwoPane) {
        if (useFoldTwoPane) {
            val entry = navController.currentBackStackEntry
            val route = entry?.destination?.route
            if (route != null && route.startsWith("detail/")) {
                val resultId = entry.arguments?.getString("resultId")
                if (!resultId.isNullOrBlank()) {
                    container.pendingFoldDetail = PendingFoldDetail(
                        resultId = resultId,
                        name = entry.arguments?.getString("name") ?: "",
                        site = entry.arguments?.getString("site") ?: "",
                    )
                }
                navController.popBackStack(Routes.SEARCH, inclusive = false)
            }
        }
    }

    NavHost(navController, startDestination = Routes.SEARCH, modifier = modifier) {
        composable(Routes.SEARCH) {
            if (useFoldTwoPane) {
                FoldSearchDetailLayout(
                    container = container,
                    searchViewModel = searchViewModel,
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                )
            } else {
                SearchScreen(
                    container = container,
                    sharedViewModel = searchViewModel,
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
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

            LaunchedEffect(
                searchState.groups,
                searchState.results.map { it.id },
                searchState.hasSearched,
                searchState.loading,
                resultId,
            ) {
                if (searchState.loading || !searchState.hasSearched) return@LaunchedEffect
                val releases = allReleases()
                if (resultId in releases.map { it.id }) return@LaunchedEffect
                val matched = rematchRelease(resultId, navName, navSite)
                if (matched != null) {
                    container.searchResultStore.put(matched)
                    if (matched.id != resultId) {
                        container.movieMetadataStore.get(resultId)?.let { meta ->
                            container.movieMetadataStore.put(matched.id, meta)
                        }
                        navController.navigate(Routes.detail(matched.id, matched.name, matched.site)) {
                            launchSingleTop = true
                            popUpTo(Routes.SEARCH) { inclusive = false }
                        }
                    }
                    return@LaunchedEffect
                }
                navController.popBackStack(Routes.SEARCH, inclusive = false)
            }

            val cached = container.searchResultStore.get(resultId)
            TorrentDetailScreen(
                container = container,
                resultId = resultId,
                name = cached?.name ?: navName,
                site = cached?.site ?: navSite,
                initialMagnet = cached?.magnet,
                onResultExpired = {
                    navController.popBackStack(Routes.SEARCH, inclusive = false)
                },
            )
        }
        composable(Routes.UPLOADED) { UploadedScreen(container) }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
        composable(Routes.HELP) { HelpScreen() }
    }
}
