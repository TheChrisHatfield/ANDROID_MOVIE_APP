package com.torrentmovie.core.data

import android.content.Context
import com.torrentmovie.core.data.db.AppDatabase

class AppContainer(context: Context) {
    /** Cover/single-pane → unfolded two-pane restore payload. */
    var pendingFoldDetail: PendingFoldDetail? = null

    /** Two-pane → narrow phone nav: push detail route when width drops. */
    var pendingFoldNarrowDetail: PendingFoldDetail? = null

    val settingsRepository = SettingsRepository(context)
    val searchResultStore = SearchResultStore()
    val movieMetadataStore = MovieMetadataStore()
    private val database = AppDatabase.get(context)
    val searchRepository = SearchRepository(settingsRepository)
    val seedboxRepository = SeedboxRepository(settingsRepository, database)
    val uploadedRepository = UploadedRepository(database)
}
