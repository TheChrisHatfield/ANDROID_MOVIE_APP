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
import com.torrentmovie.app.ui.fold.shouldRestorePhoneDetailOnFold
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
import com.torrentmovie.app.ui.uploaded.UploadedViewModel
import com.torrentmovie.app.ui.util.SearchReleaseRematch
import com.torrentmovie.app.ui.util.detailHandoffName
import com.torrentmovie.app.ui.util.detailHandoffSite
import com.torrentmovie.app.ui.util.magnetFallbackDetailUrl
import com.torrentmovie.app.ui.util.shouldClearPhoneFoldSelectionOnDetailDispose
import com.torrentmovie.app.ui.util.shouldPopExpiredPhoneDetail
import com.torrentmovie.app.ui.util.shouldReplacePhoneDetail
import com.torrentmovie.app.ui.util.snapshotGenreAtDetailOpen
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
    val uploadedViewModel: UploadedViewModel = viewModel { UploadedViewModel(container) }
    val searchState by searchViewModel.state.collectAsState()
    val foldActiveSelection by container.foldActiveSelectionFlow.collectAsState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useTwoPane = AdaptiveLayout.useTwoPaneSearchDetail(screenWidthDp)

    fun allReleases(): List<TorrentResultDto> {
        return searchState.results + searchState.groups.flatMap { it.releases }
    }

    fun rematchRelease(
        resultId: String,
        name: String,
        site: String,
        detailUrl: String? = null,
    ): TorrentResultDto? {
        return SearchReleaseRematch.find(allReleases(), resultId, name, site, detailUrl)
    }

    fun replacePhoneDetail(resultId: String, name: String, site: String) {
        navController.navigate(Routes.detail(resultId, name, site)) {
            launchSingleTop = true
            popUpTo(Routes.DETAIL) { inclusive = true }
        }
    }

    fun openSettings() {
        navController.navigate(Routes.SETTINGS) {
            launchSingleTop = true
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            restoreState = true
        }
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

    LaunchedEffect(useTwoPane, foldActiveSelection) {
        val foldingToPhone = wasTwoPane && !useTwoPane
        if (useTwoPane) {
            val entry = navController.currentBackStackEntry
            val route = entry?.destination?.route
            if (route != null && route.startsWith("detail/")) {
                val resultId = entry.arguments?.getString("resultId")
                if (!resultId.isNullOrBlank()) {
                    val stored = container.searchResultStore.get(resultId)
                    val selection = foldActiveSelection?.takeIf { it.resultId == resultId }
                    container.pendingFoldDetail = PendingFoldDetail(
                        resultId = resultId,
                        name = detailHandoffName(
                            entry.arguments?.getString("name"),
                            stored?.name,
                            selection?.name,
                        ),
                        site = detailHandoffSite(
                            entry.arguments?.getString("site"),
                            stored?.site,
                            selection?.site,
                        ),
                        genreId = snapshotGenreAtDetailOpen(
                            foldActiveSelection?.genreId,
                            searchState.activeGenre,
                        ),
                        detailUrl = magnetFallbackDetailUrl(
                            stored?.detail_url,
                            selection?.detailUrl,
                        ),
                    )
                }
                navController.popBackStack(Routes.SEARCH, inclusive = false)
            }
        } else if (shouldRestorePhoneDetailOnFold(foldingToPhone, foldActiveSelection?.resultId)) {
            val active = foldActiveSelection ?: return@LaunchedEffect
            navController.navigate(
                Routes.detail(active.resultId, active.name, active.site),
            ) {
                launchSingleTop = true
                popUpTo(Routes.SEARCH) { inclusive = false }
            }
        }
        wasTwoPane = useTwoPane
    }

    NavHost(navController, startDestination = Routes.SEARCH, modifier = modifier) {
        composable(Routes.SEARCH) {
            if (useTwoPane) {
                FoldSearchDetailLayout(
                    container = container,
                    searchViewModel = searchViewModel,
                    onOpenSettings = ::openSettings,
                    onOpenUploaded = ::openUploaded,
                )
            } else {
                SearchScreen(
                    container = container,
                    sharedViewModel = searchViewModel,
                    onOpenSettings = ::openSettings,
                    onOpenDetail = { r ->
                        container.searchResultStore.put(r)
                        container.foldActiveSelection = PendingFoldDetail(
                            resultId = r.id,
                            name = r.name,
                            site = r.site,
                            genreId = searchState.activeGenre,
                            detailUrl = r.detail_url,
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
                val matched = rematchRelease(
                    resultId,
                    navName,
                    rematchSite,
                    detailUrl = container.searchResultStore.get(resultId)?.detail_url,
                )
                if (matched != null) {
                    val oldMagnet = container.searchResultStore.get(resultId)?.magnet?.takeIf { it.isNotBlank() }
                    val merged = if (!oldMagnet.isNullOrBlank() && matched.magnet.isNullOrBlank()) {
                        matched.copy(magnet = oldMagnet)
                    } else {
                        matched
                    }
                    container.searchResultStore.put(merged)
                    if (resultId != matched.id) {
                        container.searchResultStore.remove(resultId)
                    }
                    if (shouldReplacePhoneDetail(resultId, matched.id)) {
                        container.movieMetadataStore.get(resultId)?.let { meta ->
                            container.movieMetadataStore.put(matched.id, meta)
                            container.movieMetadataStore.remove(resultId)
                            container.movieMetadataStore.bumpRevision()
                        }
                        replacePhoneDetail(matched.id, matched.name, matched.site)
                    }
                    return@LaunchedEffect
                }
                if (!shouldPopExpiredPhoneDetail(
                        storeHit = container.searchResultStore.get(resultId) != null,
                        rematchHit = false,
                        navName = navName,
                    )
                ) {
                    return@LaunchedEffect
                }
                navController.popBackStack(Routes.SEARCH, inclusive = false)
            }

            DisposableEffect(resultId, useTwoPane) {
                if (!useTwoPane) {
                    val prior = container.foldActiveSelection
                    container.foldActiveSelection = PendingFoldDetail(
                        resultId = resultId,
                        name = navName,
                        site = navSite,
                        genreId = snapshotGenreAtDetailOpen(prior?.genreId, searchState.activeGenre),
                        detailUrl = magnetFallbackDetailUrl(
                            container.searchResultStore.get(resultId)?.detail_url,
                            prior?.detailUrl,
                        ),
                    )
                }
                onDispose {
                    if (
                        !useTwoPane &&
                        shouldClearPhoneFoldSelectionOnDetailDispose(
                            navController.currentBackStackEntry?.destination?.route,
                        )
                    ) {
                        container.foldActiveSelection = null
                    }
                }
            }

            val cached = container.searchResultStore.get(resultId)
            val genreAtOpen = remember(resultId) {
                snapshotGenreAtDetailOpen(
                    foldActiveSelection?.genreId,
                    searchState.activeGenre,
                )
            }
            TorrentDetailScreen(
                container = container,
                resultId = resultId,
                name = cached?.name ?: navName,
                site = cached?.site ?: navSite,
                initialMagnet = cached?.magnet,
                detailUrl = magnetFallbackDetailUrl(
                    cached?.detail_url,
                    foldActiveSelection?.detailUrl,
                ),
                onResultExpired = {
                    container.foldActiveSelection = null
                    navController.popBackStack(Routes.SEARCH, inclusive = false)
                },
                onResultIdChanged = { newId ->
                    val stored = container.searchResultStore.get(newId)
                    val nextName = stored?.name?.takeIf { it.isNotBlank() } ?: cached?.name ?: navName
                    val nextSite = stored?.site?.takeIf { it.isNotBlank() } ?: cached?.site ?: navSite
                    if (shouldReplacePhoneDetail(resultId, newId)) {
                        replacePhoneDetail(newId, nextName, nextSite)
                    }
                },
                onOpenUploaded = ::openUploaded,
                onGenreBranchFeedback = { success ->
                    searchViewModel.recordGenreBranchFeedback(resultId, success, genreAtOpen)
                },
            )
        }
        composable(Routes.UPLOADED) {
            UploadedScreen(container, sharedViewModel = uploadedViewModel)
        }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
        composable(Routes.HELP) { HelpScreen() }
    }
}
