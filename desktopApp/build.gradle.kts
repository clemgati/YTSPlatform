import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared:app"))
    // For the application-data directory, so the log file lands beside the database rather
    // than wherever the process happened to be started from. The alternative was a second
    // copy of the per-operating-system path logic, which would drift.
    implementation(project(":shared:core:database"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // An SLF4J provider, so the libraries stop talking into a no-operation logger. See
    // src/main/resources/logback.xml for why the root level is WARN rather than INFO.
    runtimeOnly(libs.logback.classic)
}

compose.desktop {
    application {
        mainClass = "com.yellowtrack.platform.DesktopAppKt"

        // Off. Not tuned — off.
        //
        // It broke this application four separate ways, each of which only appeared in a
        // packaged build and each hidden behind the last: it renamed the four classes found
        // by name through ServiceLoader, renamed the ones the SQLite driver's native library
        // looks up through JNI, and rewrote kotlinx.coroutines into bytecode the JVM refuses
        // to load — "VerifyError: Bad invokespecial instruction" in JobSupport.cancel, which
        // no keep rule and not even -dontoptimize would settle.
        //
        // What it buys is shrinking. The installer is dominated by Skiko and a whole JDK, so
        // the saving is small against a 100MB download, and it is bought with a class of
        // failure that cannot be caught by building — only by installing and running, which
        // is the slowest loop this project has.
        //
        // See proguard-rules.pro, kept for the record of what each pass broke.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // jpackage builds a trimmed JRE with jlink, and the default set does not
            // include java.sql — so the packaged application started, opened its database
            // and died on NoClassDefFoundError: java/sql/DriverManager, before drawing a
            // window.
            //
            // Everything, rather than naming java.sql and moving on. The modules an
            // application needs are discovered by executing the path that needs them, and
            // the paths that cannot be exercised here are the ones that matter most: TLS
            // needs jdk.crypto.ec to negotiate with Let's Encrypt, and that only fails at
            // the moment a studio signs in. Naming modules one at a time means finding the
            // next one in front of somebody.
            //
            // It costs about 40MB in the installer, which is a fair price for not
            // discovering a missing module in the field.
            includeAllModules = true
            // What a person sees: the installer, the application folder, the dock. The
            // bundle identifier below stays reverse-DNS; this does not.
            packageName = "Yellow Track"
            // From the root build, like everything else that shows a version. Typed here
            // it said 1.0.0 while the application inside said 0.7.0, so a studio reporting
            // a problem would have quoted whichever it happened to be looking at.
            //
            // macOS will not accept a leading zero — jpackage refuses with "the first
            // number in an app-version cannot be zero or negative" — and this project is
            // pre-1.0, so a zero major is carried to 1 for the installer only. 0.7.0
            // installs as 1.7.0.
            //
            // The application still reports 0.7.0, which is the number to quote and the
            // one BuildInfo generates. The two agree exactly from 1.0.0 onward, and the
            // way to stop having to explain this is to release 1.0.0.
            packageVersion =
                project.version
                    .toString()
                    .split(".")
                    .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
                    .let { (it + listOf(0, 0, 0)).take(3) }
                    .let { (major, minor, patch) -> "${major.coerceAtLeast(1)}.$minor.$patch" }

            // Each platform wants its own container for the same mark: an .icns for
            // macOS, a multi-size .ico for Windows, and a plain PNG for Linux. All three
            // are cut from the same artwork and committed, because the tools that build
            // them are not on every machine that builds this.
            macOS {
                // Stated rather than left to default from packageName, which is now a
                // display name with a space in it and would make an invalid identifier.
                bundleID = "com.yellowtrack.platform"
                iconFile.set(project.file("icons/yellowtrack.icns"))
            }
            windows { iconFile.set(project.file("icons/yellowtrack.ico")) }
            linux { iconFile.set(project.file("icons/yellowtrack.png")) }
        }
    }
}
