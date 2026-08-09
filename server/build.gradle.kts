plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    // Gives `run` for development and `installDist` for deployment. Until this the server
    // had no supported way to be started, which is a strange gap in a thing whose whole
    // job is to be running.
    application
}

application {
    mainClass.set("com.yellowtrack.platform.server.ApplicationKt")
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

    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.statusPages)

    implementation(libs.hikari)
    implementation(libs.bouncycastle)
    implementation(libs.angus.mail)

    // Photographs go to S3 (ADR 0013). The url-connection client rather than the
    // default Netty one: this makes a handful of calls per event and has no use for an
    // async engine, and the lighter client keeps the deployed distribution smaller.
    implementation(libs.awssdk.s3)
    implementation(libs.awssdk.urlConnectionClient)
    runtimeOnly(libs.logback.classic)

    implementation(libs.flyway.core)
    // Since Flyway 10 the database dialects ship separately from the core; without this
    // the migration fails at run time rather than at compile time.
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.postgresql)

    // The client half, so the end-to-end test can point the *real* transport at the *real*
    // server. Until this existed the two sides agreed by inspection and had never spoken.
    testImplementation(project(":shared:core:network"))
    testImplementation(libs.ktor.client.contentNegotiation)

    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // The drift test reads the committed SQLDelight snapshot as an ordinary SQLite file.
    testImplementation(libs.sqlite.jdbc)
}

kotlin {
    jvmToolchain(21)
}
