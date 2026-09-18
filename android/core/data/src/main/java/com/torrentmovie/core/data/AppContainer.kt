package com.torrentmovie.core.data

import android.content.Context
import com.torrentmovie.core.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    /** Cover/single-pane → unfolded two-pane restore payload. */
    var pendingFoldDetail: PendingFoldDetail? = null

    /** Latest fold two-pane selection (survives layout disposal on width change). */
    var foldActiveSelection: PendingFoldDetail? = null

    /** Scroll/highlight target when opening Uploaded from detail after send. */
    var pendingUploadedHighlight: String? = null
    private val _uploadedHighlightSeq = MutableStateFlow(0L)
    val uploadedHighlightSeq: StateFlow<Long> = _uploadedHighlightSeq.asStateFlow()

    fun requestUploadedHighlight(storageKey: String) {
        pendingUploadedHighlight = storageKey
        _uploadedHighlightSeq.value += 1
    }

    val settingsRepository = SettingsRepository(context)
    val searchResultStore = SearchResultStore()
    val movieMetadataStore = MovieMetadataStore()
    private val database = AppDatabase.get(context)
    val searchRepository = SearchRepository(settingsRepository)
    val seedboxRepository = SeedboxRepository(settingsRepository, database)
    val uploadedRepository = UploadedRepository(database)
}
