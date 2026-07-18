import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":composeApp"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.decompose)
            implementation(libs.decompose.extensions.compose)
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        // Phase 0.12 — drive BASE_URL via JVM system properties so the same
        // binary can target dev (HTTP LAN IP) or prod (HTTPS) without recompile.
        // Set via `-Papi.url=https://api.temubelajar.id` at build/install time.
        val apiUrl = project.findProperty("api.url") as? String
        val apiWsUrl = project.findProperty("api.wsUrl") as? String
        if (apiUrl != null) jvmArgs("-Dapi.url=$apiUrl")
        if (apiWsUrl != null) jvmArgs("-Dapi.wsUrl=$apiWsUrl")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TemuBelajar"
            packageVersion = project.findProperty("app.version") as? String ?: "1.0.0"
        }
    }
}
