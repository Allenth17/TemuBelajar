package com.hiralen.temubelajar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform