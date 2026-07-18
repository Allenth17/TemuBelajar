package com.hiralen.temubelajar.core.di

import io.ktor.client.engine.js.Js
import org.koin.core.module.Module
import org.koin.dsl.module

actual val corePlatformModule: Module = module {
    single { Js.create() }
}
