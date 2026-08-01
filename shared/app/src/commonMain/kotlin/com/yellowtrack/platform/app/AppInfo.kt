package com.yellowtrack.platform.app

/**
 * Facts about the running application.
 *
 * One constant rather than a string typed into each screen: the sidebar previously read
 * "0.1.0" three releases after that was true, and a badge beside it still read "Genesis",
 * the codename of the very first milestone.
 *
 * Kept in step with `docs/CHANGELOG.md` by hand, and that has now failed twice: it read
 * "0.1.0" three milestones late, was corrected, and was found reading "0.4.0" three
 * milestones late again — by running the application and looking at the sidebar, which is
 * the only way a stale constant is ever noticed.
 *
 * Twice is the evidence. This should be generated from a Gradle version rather than typed,
 * and until it is, expect a third.
 */
object AppInfo {
    const val VERSION: String = "0.7.0"
}
