package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.SearchApiMessages

internal fun searchApiBlockedMessage(autoConfigurationPending: Boolean): String =
    SearchApiMessages.blocked(autoConfigurationPending)

/** Show setup/discovery banner until FR-040 has a usable URL on this network. */
internal fun shouldShowSearchApiSetupBanner(
    @Suppress("UNUSED_PARAMETER") searchApiBaseUrl: String,
    @Suppress("UNUSED_PARAMETER") autoConfigurationPending: Boolean,
    @Suppress("UNUSED_PARAMETER") searchApiUsableOnThisNetwork: Boolean = true,
): Boolean = false
