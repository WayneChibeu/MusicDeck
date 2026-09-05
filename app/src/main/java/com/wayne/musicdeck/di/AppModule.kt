package com.wayne.musicdeck.di

import com.wayne.musicdeck.MainViewModel
import com.wayne.musicdeck.data.*
import com.wayne.musicdeck.utils.SettingsManager
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singletons
    single { MusicDatabase.getDatabase(androidApplication()) }
    single { get<MusicDatabase>().playlistDao() }
    single { get<MusicDatabase>().playCountDao() }
    single { get<MusicDatabase>().customMetadataDao() }
    single { get<MusicDatabase>().hiddenSongDao() }
    single { get<MusicDatabase>().playHistoryDao() }

    // Repositories & Managers
    single { PlaylistRepository(get()) }
    single { CustomCoverRepository(androidApplication()) }
    single { LyricsRepository(androidApplication()) }
    single { SettingsManager(androidApplication()) }
    single { com.wayne.musicdeck.update.UpdateManager(androidApplication(), get()) }

    // ViewModel
    viewModel { MainViewModel(androidApplication(), get(), get(), get(), get(), get(), get(), get()) }
}
