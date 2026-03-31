plugins {
    // Root build — hanya declare plugins tanpa apply
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.composeMultiplatform).apply(false)
    alias(libs.plugins.composeCompiler).apply(false)
    alias(libs.plugins.jetbrains.kotlin.serialization).apply(false)
}
// root build.gradle.kts
