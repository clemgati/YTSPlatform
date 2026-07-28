package com.yellowtrack.platform.app

/**
 * Facts about the running application.
 *
 * One constant rather than a string typed into each screen: the sidebar previously read
 * "0.1.0" three releases after that was true, and a badge beside it still read "Genesis",
 * the codename of the very first milestone.
 *
 * Kept in step with `docs/CHANGELOG.md` by hand. It should be generated from the Gradle
 * version once one is declared there.
 */
object AppInfo {
    const val VERSION: String = "0.4.0"
}
