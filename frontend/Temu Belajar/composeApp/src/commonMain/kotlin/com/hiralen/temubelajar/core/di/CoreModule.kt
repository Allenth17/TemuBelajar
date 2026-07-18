package com.hiralen.temubelajar.core.di

import com.hiralen.temubelajar.core.data.DefaultAccountRepository
import com.hiralen.temubelajar.core.data.HttpClientFactory
import com.hiralen.temubelajar.core.data.TokenStorage
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.videochat.webrtc.WebRtcManager
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
    // Phase 1.4 — register WebRtcManager as a Koin `single` so the
    // Home screen + VideoChat screen share the same engine. Previously
    // HomeComponent + VideoChatComponent each `new`-ed their own
    // WebRtcManager, which started TWO PeerConnectionFactory instances
    // (+ two camera captures, two SurfaceTextureHelper threads, two
    // SharedEglBase instances if 1.17 regressed); the second call to
    // startCapture on the OS camera device threw "camera in use" → black
    // preview on the second screen.
    single { WebRtcManager() }
}

/** Convenience init function — call from app entry point */
fun initKoin(extraModules: List<Module> = emptyList()) {
    org.koin.core.context.startKoin {
        modules(coreModule + extraModules + corePlatformModule)
    }
}
