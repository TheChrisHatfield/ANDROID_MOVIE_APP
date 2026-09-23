package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.SearchApiMessages

internal fun searchApiBlockedMessage(autoConfigurationPending: Boolean): String =
    SearchApiMessages.blocked(autoConfigurationPending)

/** Show setup/discovery banner until FR-040 has persisted a URL (emulator dev default may be non-blank earlier). */
internal fun shouldShowSearchApiSetupBanner(
    searchApiBaseUrl: String,
    autoConfigurationPending: Boolean,
): Boolean = searchApiBaseUrl.isBlank() || autoConfigurationPending
