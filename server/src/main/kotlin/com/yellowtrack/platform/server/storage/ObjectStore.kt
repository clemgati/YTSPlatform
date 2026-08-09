package com.yellowtrack.platform.server.storage

/**
 * Where photographs live, once there are photographs.
 *
 * ADR 0013 decision 6. An interface rather than the S3 client directly, for one reason worth
 * stating: the behaviour that matters most here is what happens when a studio is deleted,
 * and that has to be testable without a bucket. `AccountDeletionTest` runs on every CI build
 * against a real Postgres and no AWS credentials.
 *
 * Deliberately small. It is not a filesystem, and every method here exists because something
 * in ADR 0013 needs it: put a photograph, hand an attendee a link that expires, and remove
 * everything a studio owns when its thirty days are up.
 */
interface ObjectStore {
    /** Stores [bytes] under [key], replacing anything already there. */
    fun put(
        key: String,
        contentType: String,
        bytes: ByteArray,
    )

    /**
     * A link that works for a while and then does not.
     *
     * Time-limited because an attendee's gallery link travels by email and lives in an inbox
     * forever after. A durable public URL would mean a photograph is readable by anyone who
     * is ever forwarded the message, long after the event and after any deletion.
     */
    fun temporaryUrl(
        key: String,
        validFor: kotlin.time.Duration,
    ): String

    /**
     * Removes [keys], and answers which are now gone.
     *
     * Returns rather than throws on a partial failure, because the purge has to be able to
     * delete the rows for the objects that *did* go and keep the rest. A method that threw
     * would force the caller to choose between losing the record of what remains and
     * retrying deletions that already succeeded.
     */
    fun delete(keys: List<String>): Set<String>

    companion object {
        /**
         * For a deployment with no bucket configured, and for tests about something else.
         *
         * Refuses to store rather than pretending to. A store that silently accepted
         * photographs and dropped them would be the worst failure this system could have —
         * the studio would be told the event was delivered.
         */
        val Unconfigured: ObjectStore =
            object : ObjectStore {
                override fun put(
                    key: String,
                    contentType: String,
                    bytes: ByteArray,
                ): Unit = throw IllegalStateException("no object storage is configured: set STORAGE_BUCKET")

                override fun temporaryUrl(
                    key: String,
                    validFor: kotlin.time.Duration,
                ): String = throw IllegalStateException("no object storage is configured: set STORAGE_BUCKET")

                /**
                 * The exception to refusing. A deployment that has never stored anything has
                 * nothing to delete, and a purge must not fail because of that — the promise
                 * being kept here is about rows as much as objects.
                 */
                override fun delete(keys: List<String>): Set<String> = keys.toSet()
            }
    }
}
