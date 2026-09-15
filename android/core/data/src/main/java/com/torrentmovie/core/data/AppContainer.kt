package com.torrentmovie.core.data

import android.content.Context
import com.torrentmovie.core.data.db.AppDatabase

class AppContainer(context: Context) {
    var pendingFoldDetailId: String? = null

    val settingsRepository = SettingsRepository(context)
    val searchResultStore = SearchResultStore()
    private val database = AppDatabase.get(context)
    val searchRepository = SearchRepository(settingsRepository)
    val seedboxRepository = SeedboxRepository(settingsRepository, database)
    val uploadedRepository = UploadedRepository(database)
}
