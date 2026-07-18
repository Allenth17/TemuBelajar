package com.hiralen.temubelajar.webrtc

import org.webrtc.EglBase

/**
 * Shared EGL context holder (Phase 1.17).
 *
 * libwebrtc's GPU pipelines (video decoders/encoders, SurfaceViewRenderer,
 * SurfaceTextureHelper) all draw through one EGL context. Re-creating that
 * context per engine instance churns the GPU driver and was the cause of
 * "black preview" after the 2nd call. Holding a single `EglBase` for the
 * lifetime of the process lets every WebRTC engine instance share the same
 * context — only the PeerConnection / tracks / capturer are torn down on
 * `dispose()`, the GPU resources stay warm.
 *
 * Usage:
 *   - Kotlin/DI: inject `SharedEglBase` (Koin `single`).
 *   - Direct: `SharedEglBase.get()` lazily provisions a process-wide instance.
 *
 * `release()` should only be called from `Application.onTerminate()` /
 * `DisposableEffect` in tests — never per-screen.
 */
object SharedEglBase {
    @Volatile private var instance: EglBase? = null

    fun get(): EglBase = instance ?: synchronized(this) {
        instance ?: EglBase.create().also { instance = it }
    }
}
