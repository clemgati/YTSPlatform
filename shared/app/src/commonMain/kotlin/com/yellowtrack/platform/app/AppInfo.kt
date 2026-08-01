package com.yellowtrack.platform.app

/**
 * Facts about the running application.
 *
 * One constant rather than a string typed into each screen: the sidebar previously read
 * "0.1.0" three releases after that was true, and a badge beside it still read "Genesis",
 * the codename of the very first milestone.
 *
 * Was typed by hand and read three milestones behind twice — "0.1.0" once, "0.4.0" again,
 * the second found only by running the application and looking at the sidebar. Twice was
 * the evidence, so it is generated from the Gradle version now and there is nothing left
 * to forget to update.
 */
object AppInfo {
    const val VERSION: String = BuildInfo.VERSION
}
