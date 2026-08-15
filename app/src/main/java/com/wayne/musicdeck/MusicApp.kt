package com.wayne.musicdeck

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.tencent.mmkv.MMKV
import com.wayne.musicdeck.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MusicApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize MMKV (Seal-style Performance)
        MMKV.initialize(this)

        // 2. Start Koin (Clean Dependency Injection)
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MusicApp)
            modules(appModule)
        }
        
        // 3. Setup Custom Coil ImageLoader for UI Album Art
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(com.wayne.musicdeck.utils.CoilAudioFetcher.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)

        // 4. Apply Material 3 Dynamic Colors conditionally based on user theme selection
        val dynamicColorsOptions = com.google.android.material.color.DynamicColorsOptions.Builder()
            .setPrecondition { activity, _ ->
                com.wayne.musicdeck.utils.ThemeHelper.isDynamicTheme(activity)
            }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions)
        
        // --- GLOBAL CRASH INTERCEPTOR (DIAGNOSTICS) ---
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashFile = java.io.File(cacheDir, "last_crash.txt")
            try {
                java.io.PrintWriter(crashFile).use { writer ->
                    writer.println("Crashed on Thread: ${thread.name}")
                    writer.println("Message: ${throwable.message}")
                    throwable.printStackTrace(writer)
                }
            } catch (e: Exception) {
                // Fail silently on logging error
            }
            // Let the system handle the crash after logging
            System.exit(1)
        }
    }
}
