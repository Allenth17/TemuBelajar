package com.hiralen.temubelajar

import android.app.Application
import com.hiralen.temubelajar.core.data.AppContext
import com.hiralen.temubelajar.core.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TeBeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        // core.di.initKoin() dengan extension setelah startKoin
        // Gunakan koin context builder secara manual untuk android
        org.koin.core.context.startKoin {
            androidLogger()
            androidContext(this@TeBeApp)
            // Module dari core diload via corePlatformModule + coreModule
            modules(
                com.hiralen.temubelajar.core.di.coreModule,
                com.hiralen.temubelajar.core.di.corePlatformModule
            )
        }
    }
}