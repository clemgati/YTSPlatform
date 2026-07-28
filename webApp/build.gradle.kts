
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// This declaration needs opt-in. Its usage should be marked with '@org.jetbrains.kotlin.gradle.ExperimentalWasmDsl' or '@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)'
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "yellow-track-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared:app"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // Copies sql.js's wasm binary to the served root — see webpack.config.d/sqljs.js.
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
        }
    }
}
