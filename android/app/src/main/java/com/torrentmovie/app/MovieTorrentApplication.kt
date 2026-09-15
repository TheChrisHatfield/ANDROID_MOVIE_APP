package com.torrentmovie.app

import android.app.Application
import com.torrentmovie.core.data.AppContainer

class MovieTorrentApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
