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
 * Breakpoints for tablet / fold inner display vs phone (portrait or landscape).
 * Two-pane and nav rail use [smallestScreenWidthDp] so rotating a phone does not
 * switch layout — only devices whose narrowest width is ≥600dp (tablet, unfolded fold).
 */
object AdaptiveLayout {
    const val TWO_PANE_MIN_WIDTH_DP = 600
    const val NAV_RAIL_MIN_WIDTH_DP = 600
    const val EXPANDED_MIN_WIDTH_DP = 840
    const val CONTENT_MAX_WIDTH_DP = 720
    const val PHONE_LANDSCAPE_MAX_HEIGHT_DP = 500

    fun useTwoPaneSearchDetail(smallestScreenWidthDp: Int): Boolean =
        smallestScreenWidthDp >= TWO_PANE_MIN_WIDTH_DP

    fun useNavigationRail(smallestScreenWidthDp: Int): Boolean =
        smallestScreenWidthDp >= NAV_RAIL_MIN_WIDTH_DP

    fun searchListPaneWeight(screenWidthDp: Int): Float =
        if (screenWidthDp >= EXPANDED_MIN_WIDTH_DP) 0.35f else 0.42f

    /** Single-pane phone turned sideways — short height, keep portrait navigation patterns. */
    fun isPhoneLandscape(
        smallestScreenWidthDp: Int,
        screenWidthDp: Int,
        screenHeightDp: Int,
    ): Boolean =
        smallestScreenWidthDp < TWO_PANE_MIN_WIDTH_DP &&
            screenWidthDp > screenHeightDp &&
            screenHeightDp <= PHONE_LANDSCAPE_MAX_HEIGHT_DP

    /** Drop the red title bar in landscape except on detail (needs Back). */
    fun hidePhoneLandscapeTitleBar(
        smallestScreenWidthDp: Int,
        screenWidthDp: Int,
        screenHeightDp: Int,
        onDetailRoute: Boolean,
    ): Boolean = isPhoneLandscape(smallestScreenWidthDp, screenWidthDp, screenHeightDp) &&
        !onDetailRoute
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
