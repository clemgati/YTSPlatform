package com.yellowtrack.platform.core.model.media

/**
 * Whether a shoot's files are actually safe, by the rule photographers are taught.
 *
 * Three copies, on at least two different kinds of storage, at least one of them away from
 * the building. Each clause exists because of a different way of losing everything: one
 * copy fails, two copies of the same kind fail together, and everything in one room burns
 * or is stolen at once.
 *
 * Modelled in `core:model` rather than in a screen because it is a rule about the studio's
 * data, not a way of drawing it, and because the answer should be identical wherever it is
 * asked.
 */
data class BackupHealth(
    val copies: Int,
    val distinctKinds: Int,
    val offsiteCopies: Int,
    /** Copies nobody has checked can still be read. */
    val unverifiedCopies: Int,
    /**
     * Copies sitting on a drive that has failed or gone missing.
     *
     * Counted, not merely subtracted, because the difference between "you have two copies"
     * and "you had three and one of them is on a dead drive" is the difference between a
     * studio that acts today and one that does not.
     */
    val unreachableCopies: Int = 0,
) {
    val hasEnoughCopies: Boolean get() = copies >= REQUIRED_COPIES

    val hasEnoughKinds: Boolean get() = distinctKinds >= REQUIRED_KINDS

    val hasOffsite: Boolean get() = offsiteCopies >= REQUIRED_OFFSITE

    val isSatisfied: Boolean get() = hasEnoughCopies && hasEnoughKinds && hasOffsite

    /**
     * What is still missing, in the order it should be fixed.
     *
     * Getting a second copy anywhere beats getting a third; getting one off the premises
     * beats spreading copies across more kinds of drive in the same room.
     */
    val shortfalls: List<String>
        get() =
            buildList {
                if (!hasEnoughCopies) {
                    val needed = REQUIRED_COPIES - copies
                    add(
                        when (copies) {
                            0 -> "No copies recorded at all"
                            else -> "$needed more ${if (needed == 1) "copy" else "copies"} needed"
                        },
                    )
                }

                if (!hasOffsite) add("Nothing is off the premises")

                // Only worth saying once there is more than one copy to spread. Telling a
                // studio with a single copy that all its copies are alike is noise, and the
                // advice it needs — get another copy — is already above.
                if (!hasEnoughKinds && copies >= 2) add("Every copy is on the same kind of storage")

                // Last, because it explains the figures above rather than adding a demand.
                if (unreachableCopies > 0) {
                    add(
                        "$unreachableCopies ${if (unreachableCopies == 1) "copy is" else "copies are"} " +
                            "on a drive that has failed",
                    )
                }
            }

    companion object {
        const val REQUIRED_COPIES = 3
        const val REQUIRED_KINDS = 2
        const val REQUIRED_OFFSITE = 1

        /**
         * Assesses a shoot's copies against the rule.
         *
         * Camera cards are excluded: the card is the original, and counting it would let a
         * studio believe it had a backup when what it had was one card.
         */
        fun of(
            copies: List<MediaCopy>,
            volumes: Map<StorageVolumeId, StorageVolume> = emptyMap(),
        ): BackupHealth {
            val real = copies.filter { it.isRealCopy }

            // A copy on a dead drive is not a copy. Where the register knows nothing about
            // a volume the copy is trusted, because absence of a record is not evidence of
            // failure — the alternative would report every studio without a register as
            // having lost everything.
            fun MediaCopy.isReachable(): Boolean = volumeId?.let { volumes[it]?.isDependable } ?: true

            val reachable = real.filter { it.isReachable() }

            return BackupHealth(
                copies = reachable.size,
                distinctKinds = reachable.map { it.kind }.distinct().size,
                offsiteCopies = reachable.count { it.isAwayFromStudio },
                unverifiedCopies = reachable.count { it.verifiedAt == null },
                unreachableCopies = real.size - reachable.size,
            )
        }
    }
}
