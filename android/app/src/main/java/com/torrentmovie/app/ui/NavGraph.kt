package com.torrentmovie.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.torrentmovie.app.ui.adaptive.AdaptiveLayout
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
import com.torrentmovie.app.ui.util.SearchReleaseRematch
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
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel(container) }
    val searchState by searchViewModel.state.collectAsState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useTwoPane = AdaptiveLayout.useTwoPaneSearchDetail(screenWidthDp)

    fun allReleases(): List<TorrentResultDto> {
        return searchState.results + searchState.groups.flatMap { it.releases }
    }

    fun rematchRelease(resultId: String, name: String, site: String): TorrentResultDto? {
        return SearchReleaseRematch.find(allReleases(), resultId, name, site)
    }

    fun openUploaded(storageKey: String) {
        container.requestUploadedHighlight(storageKey)
        navController.navigate(Routes.UPLOADED) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    var wasTwoPane by remember { mutableStateOf(useTwoPane) }

    LaunchedEffect(useTwoPane) {
        if (useTwoPane) {
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
        wasTwoPane = useTwoPane
    }

    LaunchedEffect(useTwoPane, searchState.loading, container.foldActiveSelection) {
        if (useTwoPane || searchState.loading) return@LaunchedEffect
        val active = container.foldActiveSelection ?: return@LaunchedEffect
        val route = navController.currentBackStackEntry?.destination?.route
        if (route != null && route.startsWith("detail/")) return@LaunchedEffect
        navController.navigate(
            Routes.detail(active.resultId, active.name, active.site),
        ) {
            launchSingleTop = true
            popUpTo(Routes.SEARCH) { inclusive = false }
        }
    }

    NavHost(navController, startDestination = Routes.SEARCH, modifier = modifier) {
        composable(Routes.SEARCH) {
            if (useTwoPane) {
                FoldSearchDetailLayout(
                    container = container,
                    searchViewModel = searchViewModel,
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            restoreState = true
                        }
                    },
                    onOpenUploaded = ::openUploaded,
                )
            } else {
                SearchScreen(
                    container = container,
                    sharedViewModel = searchViewModel,
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            restoreState = true
                        }
                    },
                    onOpenDetail = { r ->
                        container.searchResultStore.put(r)
                        container.foldActiveSelection = PendingFoldDetail(
                            resultId = r.id,
                            name = r.name,
                            site = r.site,
                        )
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
                val storedSite = container.searchResultStore.get(resultId)?.site
                val rematchSite = navSite.takeIf { it.isNotBlank() } ?: storedSite.orEmpty()
                val matched = rematchRelease(resultId, navName, rematchSite)
                if (matched != null) {
                    val oldMagnet = container.searchResultStore.get(resultId)?.magnet?.takeIf { it.isNotBlank() }
                    val merged = if (!oldMagnet.isNullOrBlank() && matched.magnet.isNullOrBlank()) {
                        matched.copy(magnet = oldMagnet)
                    } else {
                        matched
                    }
                    container.searchResultStore.put(merged)
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
                if (container.searchResultStore.get(resultId) != null) return@LaunchedEffect
                if (navName.isNotBlank()) return@LaunchedEffect
                navController.popBackStack(Routes.SEARCH, inclusive = false)
            }

            DisposableEffect(resultId, useTwoPane) {
                if (!useTwoPane) {
                    container.foldActiveSelection = PendingFoldDetail(
                        resultId = resultId,
                        name = navName,
                        site = navSite,
                    )
                }
                onDispose {
                    if (!useTwoPane) {
                        container.foldActiveSelection = null
                    }
                }
            }

            val cached = container.searchResultStore.get(resultId)
            TorrentDetailScreen(
                container = container,
                resultId = resultId,
                name = cached?.name ?: navName,
                site = cached?.site ?: navSite,
                initialMagnet = cached?.magnet,
                onResultExpired = {
                    container.foldActiveSelection = null
                    navController.popBackStack(Routes.SEARCH, inclusive = false)
                },
                onOpenUploaded = ::openUploaded,
            )
        }
        composable(Routes.UPLOADED) { UploadedScreen(container) }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
        composable(Routes.HELP) { HelpScreen() }
    }
}
