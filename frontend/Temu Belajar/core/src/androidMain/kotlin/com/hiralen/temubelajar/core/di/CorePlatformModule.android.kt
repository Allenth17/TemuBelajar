package com.hiralen.temubelajar.core.di

import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module
import com.hiralen.temubelajar.core.data.AppContext

actual val corePlatformModule: Module = module {
    single { OkHttp.create() }
    // Init AppContext untuk TokenStorage (SharedPreferences)
    single { AppContext.init(androidApplication()); AppContext.get() }
}
