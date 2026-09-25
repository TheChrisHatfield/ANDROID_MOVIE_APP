package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.SearchApiMessages

internal fun searchApiBlockedMessage(autoConfigurationPending: Boolean): String =
    SearchApiMessages.blocked(autoConfigurationPending)

/** Show setup/discovery banner until FR-040 has a usable URL on this network. */
internal fun shouldShowSearchApiSetupBanner(
    searchApiBaseUrl: String,
    autoConfigurationPending: Boolean,
    searchApiUsableOnThisNetwork: Boolean = true,
): Boolean = searchApiBaseUrl.isBlank() || autoConfigurationPending || !searchApiUsableOnThisNetwork
