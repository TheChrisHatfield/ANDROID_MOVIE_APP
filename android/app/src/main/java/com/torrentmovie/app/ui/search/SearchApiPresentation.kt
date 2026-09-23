package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.SearchApiMessages

internal fun searchApiBlockedMessage(autoConfigurationPending: Boolean): String =
    SearchApiMessages.blocked(autoConfigurationPending)
