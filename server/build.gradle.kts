plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

// The point of this module, and of ADR 0007: it depends on the same `core:model` the
// clients do. One definition of every entity is compiled into both sides, so adding a
// field is a compile error rather than a runtime surprise.
//
// Plain Kotlin/JVM rather than a Kotlin Multiplatform module — nothing here ships to a
// phone, and a single-target module keeps the server off the Apple half of CI entirely.
dependencies {
    implementation(project(":shared:core:model"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.flyway.core)
    // Since Flyway 10 the database dialects ship separately from the core; without this
    // the migration fails at run time rather than at compile time.
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.postgresql)

    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // The drift test reads the committed SQLDelight snapshot as an ordinary SQLite file.
    testImplementation(libs.sqlite.jdbc)
}

kotlin {
    jvmToolchain(21)
}
