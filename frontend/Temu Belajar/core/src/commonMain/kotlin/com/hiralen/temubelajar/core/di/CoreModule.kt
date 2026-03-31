package com.hiralen.temubelajar.core.di

import com.hiralen.temubelajar.core.data.DefaultAccountRepository
import com.hiralen.temubelajar.core.data.HttpClientFactory
import com.hiralen.temubelajar.core.data.TokenStorage
import com.hiralen.temubelajar.core.domain.AccountRepository
import io.ktor.client.engine.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect val corePlatformModule: Module

val coreModule = module {
    single { TokenStorage() }
    single { HttpClientFactory.create(get<HttpClientEngine>()) }
    single<AccountRepository> { DefaultAccountRepository(get(), get()) }
}

/** Convenience init function — call from app entry point */
fun initKoin(extraModules: List<Module> = emptyList()) {
    org.koin.core.context.startKoin {
        modules(coreModule + extraModules + corePlatformModule)
    }
}
