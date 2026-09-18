package com.torrentmovie.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Width breakpoints for phone, tablet, fold unfolded, and landscape layouts.
 * Two-pane search+detail and navigation rail activate from [TWO_PANE_MIN_WIDTH_DP].
 */
object AdaptiveLayout {
    const val TWO_PANE_MIN_WIDTH_DP = 600
    const val NAV_RAIL_MIN_WIDTH_DP = 600
    const val EXPANDED_MIN_WIDTH_DP = 840
    const val CONTENT_MAX_WIDTH_DP = 720

    fun useTwoPaneSearchDetail(screenWidthDp: Int): Boolean =
        screenWidthDp >= TWO_PANE_MIN_WIDTH_DP

    fun useNavigationRail(screenWidthDp: Int): Boolean =
        screenWidthDp >= NAV_RAIL_MIN_WIDTH_DP

    fun searchListPaneWeight(screenWidthDp: Int): Float =
        if (screenWidthDp >= EXPANDED_MIN_WIDTH_DP) 0.35f else 0.42f
}

@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = AdaptiveLayout.CONTENT_MAX_WIDTH_DP.dp)
                .then(scrollModifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content,
        )
    }
}
