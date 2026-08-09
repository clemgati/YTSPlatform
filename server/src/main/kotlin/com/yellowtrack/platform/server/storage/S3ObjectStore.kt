package com.yellowtrack.platform.server.storage

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import java.time.Duration as JavaDuration

/** Bucket and region. Null when the deployment has no storage, which is not an error. */
data class StorageConfig(
    val bucket: String,
    val region: String,
) {
    companion object {
        /**
         * Region defaults to the instance's own, which is also where SES is verified. A
         * bucket in another region works and costs more in transfer for no benefit anybody
         * has asked for.
         */
        fun fromEnvironment(): StorageConfig? {
            val bucket = System.getenv("STORAGE_BUCKET")?.takeIf { it.isNotBlank() } ?: return null

            return StorageConfig(
                bucket = bucket,
                region = System.getenv("STORAGE_REGION")?.takeIf { it.isNotBlank() } ?: "us-west-1",
            )
        }
    }
}

/**
 * S3, reached with whatever credentials the environment already provides.
 *
 * No keys are read from configuration on purpose. On the instance this is the EC2 role,
 * which `docs/DEPLOYMENT.md` deliberately keeps almost powerless — the alternative is a pair
 * of long-lived access keys in `/etc/yellowtrack/env`, which is one more secret to rotate and
 * one more thing to leak.
 *
 * Objects are **never public**. Reads go through [temporaryUrl], so the bucket can block all
 * public access and a photograph is readable only by somebody holding a link that expires.
 */
class S3ObjectStore(
    private val config: StorageConfig,
    private val client: S3Client =
        S3Client
            .builder()
            .region(Region.of(config.region))
            .build(),
    private val presigner: S3Presigner =
        S3Presigner
            .builder()
            .region(Region.of(config.region))
            .build(),
) : ObjectStore {
    override fun put(
        key: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        client.putObject(
            PutObjectRequest
                .builder()
                .bucket(config.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun temporaryUrl(
        key: String,
        validFor: Duration,
    ): String =
        presigner
            .presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(validFor.toJavaDurationClamped())
                    .getObjectRequest(
                        GetObjectRequest
                            .builder()
                            .bucket(config.bucket)
                            .key(key)
                            .build(),
                    ).build(),
            ).url()
            .toString()

    /**
     * One request per key rather than a batch delete.
     *
     * Slower, and chosen because the caller needs to know *which* keys went. A batched
     * `DeleteObjects` reports failures per key too, but a partial failure there is easy to
     * read as total success — and the thing this feeds is a promise that a studio's
     * photographs are gone.
     *
     * A key that is already absent counts as deleted. A purge that has run before and
     * crashed halfway must be able to finish rather than fail on what it already did.
     */
    override fun delete(keys: List<String>): Set<String> =
        keys
            .filterTo(mutableSetOf()) { key ->
                runCatching {
                    client.deleteObject(
                        DeleteObjectRequest
                            .builder()
                            .bucket(config.bucket)
                            .key(key)
                            .build(),
                    )
                }.fold(onSuccess = { true }, onFailure = { it is NoSuchKeyException })
            }
}

/**
 * S3 refuses a presigned URL valid for more than seven days, and answers with an exception
 * rather than a shorter link. Clamped here so a caller asking for a month gets the longest
 * link S3 will sign instead of a failure at the moment an attendee opens their email.
 */
private fun Duration.toJavaDurationClamped(): JavaDuration {
    val sevenDays = JavaDuration.ofDays(7)
    val requested = toJavaDuration()

    return if (requested > sevenDays) sevenDays else requested
}
