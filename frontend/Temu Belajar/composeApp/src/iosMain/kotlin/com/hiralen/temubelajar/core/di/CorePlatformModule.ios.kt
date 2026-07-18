package com.hiralen.temubelajar.core.di

import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val corePlatformModule: Module = module {
    single { Darwin.create() }
}
